package agent.memory.core

import agent.capability.AgentCapability
import agent.core.AgentTokenStats
import agent.lifecycle.AgentLifecycleListener
import agent.lifecycle.ContextCompressionStats
import agent.lifecycle.NoOpAgentLifecycleListener
import agent.memory.layer.MemoryLayerAllocator
import agent.memory.layer.MemoryLayerWritePolicy
import agent.memory.layer.RuleBasedMemoryLayerAllocator
import agent.memory.layer.UserMessageOnlyMemoryLayerWritePolicy
import agent.memory.model.MemorySnapshot
import agent.memory.model.MemoryState
import agent.memory.model.ShortTermMemory
import agent.memory.persistence.JsonMemoryStateRepository
import agent.memory.persistence.MemoryStateRepository
import agent.memory.prompt.DefaultMemoryContextService
import agent.memory.prompt.LayeredMemoryPromptAssembler
import agent.memory.prompt.MemoryContextService
import agent.memory.strategy.MemoryStrategyType
import agent.memory.strategy.branching.BranchCoordinator
import agent.memory.strategy.branching.BranchingCapability
import agent.memory.strategy.branching.BranchingMemoryCapabilityAdapter
import agent.memory.strategy.nocompression.NoCompressionMemoryStrategy
import java.nio.file.Path
import llm.core.LanguageModel
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

/**
 * Базовый in-memory менеджер диалога с явной layered memory model.
 */
class DefaultMemoryManager(
    private val languageModel: LanguageModel,
    private val systemPrompt: String,
    private val memoryStateRepository: MemoryStateRepository = JsonMemoryStateRepository.forLanguageModel(languageModel),
    private val memoryStrategy: MemoryStrategy = NoCompressionMemoryStrategy(),
    private val lifecycleListener: AgentLifecycleListener = NoOpAgentLifecycleListener,
    private val branchCoordinator: BranchCoordinator = BranchCoordinator(),
    private val memoryLayerAllocator: MemoryLayerAllocator = RuleBasedMemoryLayerAllocator(),
    private val memoryLayerWritePolicy: MemoryLayerWritePolicy = UserMessageOnlyMemoryLayerWritePolicy(),
    private val clearPolicy: MemoryClearPolicy = TaskScopedMemoryClearPolicy(),
    private val compressionObserver: MemoryCompressionObserver = SummaryBasedMemoryCompressionObserver(),
    private val contextService: MemoryContextService = DefaultMemoryContextService(
        memoryStrategyProvider = { memoryStrategy },
        promptAssembler = LayeredMemoryPromptAssembler()
    )
) : MemoryManager {
    private var state = loadMemoryState()

    private val branchingCapability = BranchingMemoryCapabilityAdapter(
        branchCoordinator = branchCoordinator,
        enabled = { memoryStrategy.type == MemoryStrategyType.BRANCHING },
        stateProvider = { state },
        stateUpdater = { updatedState -> state = updatedState },
        persistState = ::saveState
    )

    override fun currentConversation(): List<ChatMessage> = state.shortTerm.messages.toList()

    override fun previewTokenStats(userPrompt: String): AgentTokenStats {
        val effectiveConversation = contextService.effectiveConversation(systemPrompt, state)
        val historyTokens = languageModel.tokenCounter?.countMessages(effectiveConversation)
        val userPromptTokens = languageModel.tokenCounter?.countText(userPrompt)
        val promptTokensLocal = contextService.countPromptTokens(
            languageModel = languageModel,
            systemPrompt = systemPrompt,
            state = previewStateForUserPrompt(userPrompt)
        )

        return AgentTokenStats(
            historyTokens = historyTokens,
            promptTokensLocal = promptTokensLocal,
            userPromptTokens = userPromptTokens
        )
    }

    override fun appendUserMessage(userPrompt: String): List<ChatMessage> {
        val userMessage = ChatMessage(role = ChatRole.USER, content = userPrompt)
        val stateWithMessage = appendShortTermMessage(state, userMessage)
        val stateWithAllocatedLayers = applyAllocationIfAllowed(stateWithMessage, userMessage)
        state = refreshState(stateWithAllocatedLayers, notifyCompression = true)
        saveState()
        return contextService.effectiveConversation(systemPrompt, state)
    }

    override fun appendAssistantMessage(content: String) {
        val assistantMessage = ChatMessage(role = ChatRole.ASSISTANT, content = content)
        val stateWithMessage = appendShortTermMessage(state, assistantMessage)
        val stateWithAllocatedLayers = applyAllocationIfAllowed(stateWithMessage, assistantMessage)
        state =
            if (memoryStrategy.type == MemoryStrategyType.BRANCHING) {
                memoryStrategy.refreshState(stateWithAllocatedLayers, MemoryStateRefreshMode.REGULAR)
            } else {
                stateWithAllocatedLayers
            }
        saveState()
    }

    override fun clear() {
        state = clearPolicy.clear(
            state = state,
            systemMessage = createSystemMessage(),
            memoryStrategy = memoryStrategy
        )
        saveState()
    }

    override fun replaceContextFromFile(sourcePath: Path) {
        val importedState = memoryStateRepository.loadFrom(sourcePath)
        require(importedState.shortTerm.messages.isNotEmpty()) {
            "Файл истории $sourcePath пустой или не содержит сообщений."
        }

        state = memoryStrategy.refreshState(importedState, MemoryStateRefreshMode.REGULAR)
        saveState()
    }

    override fun memoryState(): MemoryState = state

    override fun memorySnapshot(): MemorySnapshot =
        MemorySnapshot(
            state = state,
            shortTermStrategyType = memoryStrategy.type
        )

    override fun <TCapability : AgentCapability> capability(capabilityType: Class<TCapability>): TCapability? =
        branchingCapability
            .takeIf { memoryStrategy.type == MemoryStrategyType.BRANCHING && capabilityType.isInstance(it) }
            ?.let(capabilityType::cast)

    private fun loadMemoryState(): MemoryState {
        val savedState = memoryStateRepository.load()
        if (savedState.shortTerm.messages.isNotEmpty()) {
            return memoryStrategy.refreshState(savedState, MemoryStateRefreshMode.REGULAR)
        }

        val initialState = memoryStrategy.refreshState(
            MemoryState(shortTerm = ShortTermMemory(messages = listOf(createSystemMessage()))),
            MemoryStateRefreshMode.REGULAR
        )
        saveState(initialState)
        return initialState
    }

    private fun saveState() {
        saveState(state)
    }

    private fun saveState(updatedState: MemoryState) {
        state = updatedState
        memoryStateRepository.save(state)
    }

    private fun createSystemMessage(): ChatMessage =
        ChatMessage(
            role = ChatRole.SYSTEM,
            content = systemPrompt
        )

    private fun previewStateForUserPrompt(userPrompt: String): MemoryState {
        val userMessage = ChatMessage(role = ChatRole.USER, content = userPrompt)
        return refreshState(
            applyAllocationIfAllowed(
                appendShortTermMessage(state, userMessage),
                userMessage
            ),
            notifyCompression = false,
            mode = MemoryStateRefreshMode.PREVIEW
        )
    }

    private fun refreshState(
        updatedState: MemoryState,
        notifyCompression: Boolean,
        mode: MemoryStateRefreshMode = MemoryStateRefreshMode.REGULAR
    ): MemoryState {
        val refreshedState = memoryStrategy.refreshState(updatedState, mode)
        val compressionStats =
            if (notifyCompression) {
                compressionObserver.buildStats(
                    previousState = updatedState,
                    refreshedState = refreshedState,
                    countTokens = ::countPromptTokens
                )
            } else {
                null
            }
        if (compressionStats == null) {
            return refreshedState
        }

        lifecycleListener.onContextCompressionStarted()
        lifecycleListener.onContextCompressionFinished(compressionStats)

        return refreshedState
    }

    private fun countPromptTokens(state: MemoryState): Int? =
        contextService.countPromptTokens(
            languageModel = languageModel,
            systemPrompt = systemPrompt,
            state = state
        )

    private fun appendShortTermMessage(currentState: MemoryState, message: ChatMessage): MemoryState =
        currentState.copy(
            shortTerm = currentState.shortTerm.copy(
                messages = currentState.shortTerm.messages + message
            )
        )

    private fun applyAllocationIfAllowed(currentState: MemoryState, message: ChatMessage): MemoryState {
        if (!memoryLayerWritePolicy.shouldAllocate(message)) {
            return currentState
        }

        val allocation = memoryLayerAllocator.allocate(currentState, message)
        return currentState.copy(
            working = allocation.workingMemory,
            longTerm = allocation.longTermMemory
        )
    }
}
