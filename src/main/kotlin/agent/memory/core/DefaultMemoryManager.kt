package agent.memory.core

import agent.core.AgentTokenStats
import agent.core.BranchCheckpointInfo
import agent.core.BranchInfo
import agent.core.BranchingStatus
import agent.memory.model.BranchCheckpointState
import agent.memory.model.BranchConversationState
import agent.memory.model.BranchingStrategyState
import agent.lifecycle.AgentLifecycleListener
import agent.lifecycle.ContextCompressionStats
import agent.lifecycle.NoOpAgentLifecycleListener
import agent.memory.model.ConversationSummary
import agent.memory.model.MemoryMetadata
import agent.memory.model.MemoryState
import agent.memory.model.StickyFactsStrategyState
import agent.memory.model.StrategyState
import agent.memory.model.SummaryStrategyState
import agent.memory.strategy.MemoryStrategyType
import agent.memory.strategy.NoCompressionMemoryStrategy
import agent.storage.JsonConversationStore
import agent.storage.mapper.ChatMessageConversationMapper
import agent.storage.model.ConversationMemoryState
import agent.storage.model.StoredBranchCheckpoint
import agent.storage.model.StoredBranchConversation
import agent.storage.model.StoredMemoryMetadata
import agent.storage.model.StoredStrategyState
import agent.storage.model.StoredSummary
import java.nio.file.Path
import llm.core.LanguageModel
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

/**
 * Базовый in-memory менеджер диалога, используемый агентом.
 *
 * Хранит текущее состояние памяти, делегирует подготовку prompt в [MemoryStrategy], сохраняет
 * состояние на диск и сообщает статистику сжатия через [AgentLifecycleListener].
 */
class DefaultMemoryManager(
    private val languageModel: LanguageModel,
    private val systemPrompt: String,
    private val conversationStore: JsonConversationStore = JsonConversationStore.forLanguageModel(languageModel),
    private val memoryStrategy: MemoryStrategy = NoCompressionMemoryStrategy(),
    private val lifecycleListener: AgentLifecycleListener = NoOpAgentLifecycleListener
) : MemoryManager {
    private val conversationMapper = ChatMessageConversationMapper()
    private var memoryState = loadMemoryState()

    override fun currentConversation(): List<ChatMessage> = memoryState.messages.toList()

    override fun previewTokenStats(userPrompt: String): AgentTokenStats {
        val effectiveConversation = effectiveConversation()
        val historyTokens = languageModel.tokenCounter?.countMessages(effectiveConversation)
        val userPromptTokens = languageModel.tokenCounter?.countText(userPrompt)
        val promptTokensLocal = languageModel.tokenCounter?.countMessages(
            effectiveConversationWithUserPrompt(userPrompt)
        )

        return AgentTokenStats(
            historyTokens = historyTokens,
            promptTokensLocal = promptTokensLocal,
            userPromptTokens = userPromptTokens
        )
    }

    override fun appendUserMessage(userPrompt: String): List<ChatMessage> {
        val stateWithUserMessage = memoryState.copy(
            messages = memoryState.messages + ChatMessage(role = ChatRole.USER, content = userPrompt)
        )
        memoryState = refreshState(stateWithUserMessage, notifyCompression = true)
        saveState()
        return effectiveConversation()
    }

    override fun appendAssistantMessage(content: String) {
        val updatedState = memoryState.copy(
            messages = memoryState.messages + ChatMessage(role = ChatRole.ASSISTANT, content = content)
        )
        memoryState =
            if (memoryStrategy.type == MemoryStrategyType.BRANCHING) {
                synchronizeStrategyId(memoryStrategy.refreshState(updatedState, MemoryStateRefreshMode.REGULAR))
            } else {
                updatedState
            }
        saveState()
    }

    override fun clear() {
        memoryState = MemoryState(
            messages = listOf(createSystemMessage()),
            metadata = MemoryMetadata(strategyType = memoryStrategy.type)
        )
        saveState()
    }

    override fun replaceContextFromFile(sourcePath: Path) {
        val importedState = JsonConversationStore(sourcePath).loadState().toMemoryState()
        require(importedState.messages.isNotEmpty()) {
            "Файл истории $sourcePath пустой или не содержит сообщений."
        }

        memoryState = synchronizeStrategyId(
            memoryStrategy.refreshState(importedState, MemoryStateRefreshMode.REGULAR)
        )
        saveState()
    }

    override fun createCheckpoint(name: String?): BranchCheckpointInfo {
        val branchingState = requireBranchingState()
        val checkpointName = normalizeBranchingName(name)
            ?: "checkpoint-${branchingState.checkpoints.size + 1}"
        require(branchingState.checkpoints.none { it.name.equals(checkpointName, ignoreCase = true) }) {
            "Checkpoint '$checkpointName' уже существует."
        }

        memoryState = memoryState.copy(
            strategyState = branchingState.copy(
                latestCheckpointName = checkpointName,
                checkpoints = branchingState.checkpoints + BranchCheckpointState(
                    name = checkpointName,
                    messages = memoryState.messages
                )
            )
        )
        saveState()

        return BranchCheckpointInfo(
            name = checkpointName,
            sourceBranchName = branchingState.activeBranchName
        )
    }

    override fun createBranch(name: String): BranchInfo {
        val branchingState = requireBranchingState()
        val branchName = normalizeRequiredBranchingName(name, "ветки")
        require(branchingState.branches.none { it.name.equals(branchName, ignoreCase = true) }) {
            "Ветка '$branchName' уже существует."
        }

        val checkpointName = branchingState.latestCheckpointName
            ?: error("Сначала создайте checkpoint командой checkpoint.")
        val checkpoint = branchingState.checkpoints.firstOrNull { it.name == checkpointName }
            ?: error("Последний checkpoint '$checkpointName' не найден.")

        memoryState = memoryState.copy(
            strategyState = branchingState.copy(
                branches = branchingState.branches + BranchConversationState(
                    name = branchName,
                    sourceCheckpointName = checkpoint.name,
                    messages = checkpoint.messages
                )
            )
        )
        saveState()

        return BranchInfo(
            name = branchName,
            sourceCheckpointName = checkpoint.name,
            isActive = false
        )
    }

    override fun switchBranch(name: String): BranchInfo {
        val branchingState = requireBranchingState()
        val branchName = normalizeRequiredBranchingName(name, "ветки")
        val branch = branchingState.branches.firstOrNull { it.name.equals(branchName, ignoreCase = true) }
            ?: error("Ветка '$branchName' не найдена.")

        memoryState = memoryState.copy(
            messages = branch.messages,
            strategyState = branchingState.copy(activeBranchName = branch.name)
        )
        saveState()

        return BranchInfo(
            name = branch.name,
            sourceCheckpointName = branch.sourceCheckpointName,
            isActive = true
        )
    }

    override fun branchStatus(): BranchingStatus {
        val branchingState = requireBranchingState()
        return BranchingStatus(
            activeBranchName = branchingState.activeBranchName,
            latestCheckpointName = branchingState.latestCheckpointName,
            branches = branchingState.branches.map { branch ->
                BranchInfo(
                    name = branch.name,
                    sourceCheckpointName = branch.sourceCheckpointName,
                    isActive = branch.name == branchingState.activeBranchName
                )
            }
        )
    }

    /**
     * Загружает сохранённое состояние памяти с диска или создаёт новое с системным сообщением.
     */
    private fun loadMemoryState(): MemoryState {
        val savedState = conversationStore.loadState().toMemoryState()
        if (savedState.messages.isNotEmpty()) {
            return synchronizeStrategyId(
                memoryStrategy.refreshState(savedState, MemoryStateRefreshMode.REGULAR)
            )
        }

        val initialState = MemoryState(
            messages = listOf(createSystemMessage()),
            metadata = MemoryMetadata(strategyType = memoryStrategy.type)
        )
        saveState(initialState)
        return initialState
    }

    private fun saveState() {
        saveState(memoryState)
    }

    /**
     * Сохраняет текущее состояние памяти, синхронизируя идентификатор активной стратегии.
     */
    private fun saveState(state: MemoryState) {
        memoryState = synchronizeStrategyId(state)
        conversationStore.saveState(memoryState.toStoredState())
    }

    /**
     * Синхронизирует metadata с текущей активной стратегией после того,
     * как стратегия уже обработала входное состояние.
     */
    private fun synchronizeStrategyId(state: MemoryState): MemoryState =
        state.copy(
            metadata = state.metadata.copy(strategyType = memoryStrategy.type)
        )

    /**
     * Формирует базовое системное сообщение для нового или очищенного диалога.
     */
    private fun createSystemMessage(): ChatMessage =
        ChatMessage(
            role = ChatRole.SYSTEM,
            content = systemPrompt
        )

    /**
     * Возвращает эффективный контекст для текущего состояния согласно активной стратегии.
     */
    private fun effectiveConversation(): List<ChatMessage> =
        memoryStrategy.effectiveContext(memoryState)

    /**
     * Строит предварительный эффективный контекст для гипотетического следующего сообщения.
     */
    private fun effectiveConversationWithUserPrompt(userPrompt: String): List<ChatMessage> =
        memoryStrategy.effectiveContext(
            refreshState(
                memoryState.copy(
                    messages = memoryState.messages + ChatMessage(role = ChatRole.USER, content = userPrompt)
                ),
                notifyCompression = false,
                mode = MemoryStateRefreshMode.PREVIEW
            )
        )

    /**
     * Применяет стратегию памяти к переданному состоянию и при необходимости сообщает статистику
     * сжатия.
     */
    private fun refreshState(
        state: MemoryState,
        notifyCompression: Boolean,
        mode: MemoryStateRefreshMode = MemoryStateRefreshMode.REGULAR
    ): MemoryState {
        val refreshedState = synchronizeStrategyId(memoryStrategy.refreshState(state, mode))
        if (!notifyCompression || !compressionApplied(state, refreshedState)) {
            return refreshedState
        }

        lifecycleListener.onContextCompressionStarted()
        lifecycleListener.onContextCompressionFinished(
            ContextCompressionStats(
                tokensBefore = languageModel.tokenCounter?.countMessages(memoryStrategy.effectiveContext(state)),
                tokensAfter = languageModel.tokenCounter?.countMessages(memoryStrategy.effectiveContext(refreshedState))
            )
        )

        return refreshedState
    }

    /**
     * Определяет, изменилось ли покрытие истории rolling summary на последнем проходе.
     */
    private fun compressionApplied(previousState: MemoryState, refreshedState: MemoryState): Boolean =
        refreshedState.metadata.compressedMessagesCount > previousState.metadata.compressedMessagesCount

    /**
     * Преобразует сохранённую JSON-модель в runtime-модель памяти.
     */
    private fun ConversationMemoryState.toMemoryState(): MemoryState =
        MemoryState(
            messages = messages.map(conversationMapper::fromStoredMessage),
            strategyState = toRuntimeStrategyState(),
            metadata = MemoryMetadata(
                strategyType = metadata.strategyId?.let(MemoryStrategyType::fromId),
                compressedMessagesCount = metadata.compressedMessagesCount
            )
        )

    /**
     * Преобразует strategy-specific persisted state в runtime-state.
     */
    private fun ConversationMemoryState.toRuntimeStrategyState(): StrategyState? {
        val storedStrategyState = strategyState
        if (storedStrategyState == null) {
            return null
        }

        return when (storedStrategyState.strategyType?.let(MemoryStrategyType::fromId)) {
            MemoryStrategyType.SUMMARY_COMPRESSION -> SummaryStrategyState(
                summary = storedStrategyState.summary?.toRuntimeSummary()
            )
            MemoryStrategyType.STICKY_FACTS -> StickyFactsStrategyState(
                facts = storedStrategyState.facts,
                coveredMessagesCount = storedStrategyState.factsCoveredMessagesCount
            )
            MemoryStrategyType.BRANCHING -> BranchingStrategyState(
                activeBranchName = storedStrategyState.activeBranchName ?: BranchingStrategyState.DEFAULT_BRANCH_NAME,
                latestCheckpointName = storedStrategyState.latestCheckpointName,
                checkpoints = storedStrategyState.checkpoints.map { checkpoint ->
                    BranchCheckpointState(
                        name = checkpoint.name,
                        messages = checkpoint.messages.map(conversationMapper::fromStoredMessage)
                    )
                },
                branches = storedStrategyState.branches.map { branch ->
                    BranchConversationState(
                        name = branch.name,
                        sourceCheckpointName = branch.sourceCheckpointName,
                        messages = branch.messages.map(conversationMapper::fromStoredMessage)
                    )
                }
            )
            else -> null
        }
    }

    /**
     * Преобразует runtime-модель памяти в сохраняемое JSON-представление.
     */
    private fun MemoryState.toStoredState(): ConversationMemoryState =
        ConversationMemoryState(
            messages = messages.map(conversationMapper::toStoredMessage),
            strategyState = strategyState?.toStoredStrategyState(),
            metadata = StoredMemoryMetadata(
                strategyId = metadata.strategyType?.id,
                compressedMessagesCount = metadata.compressedMessagesCount
            )
        )

    private fun StoredSummary.toRuntimeSummary(): ConversationSummary =
        ConversationSummary(
            content = content,
            coveredMessagesCount = coveredMessagesCount
        )

    private fun ConversationSummary.toStoredSummary(): StoredSummary =
        StoredSummary(
            content = content,
            coveredMessagesCount = coveredMessagesCount
        )

    private fun StrategyState.toStoredStrategyState(): StoredStrategyState =
        when (this) {
            is StickyFactsStrategyState -> StoredStrategyState(
                strategyType = strategyType.id,
                facts = facts,
                factsCoveredMessagesCount = coveredMessagesCount
            )
            is BranchingStrategyState -> StoredStrategyState(
                strategyType = strategyType.id,
                activeBranchName = activeBranchName,
                latestCheckpointName = latestCheckpointName,
                checkpoints = checkpoints.map { checkpoint ->
                    StoredBranchCheckpoint(
                        name = checkpoint.name,
                        messages = checkpoint.messages.map(conversationMapper::toStoredMessage)
                    )
                },
                branches = branches.map { branch ->
                    StoredBranchConversation(
                        name = branch.name,
                        sourceCheckpointName = branch.sourceCheckpointName,
                        messages = branch.messages.map(conversationMapper::toStoredMessage)
                    )
                }
            )
            is SummaryStrategyState -> StoredStrategyState(
                strategyType = strategyType.id,
                summary = summary?.toStoredSummary()
            )
        }

    private fun requireBranchingState(): BranchingStrategyState {
        require(memoryStrategy.type == MemoryStrategyType.BRANCHING) {
            "Команды ветвления доступны только для стратегии Branching."
        }

        val branchingState = memoryState.strategyState as? BranchingStrategyState
        return branchingState
            ?: error("Состояние ветвления не инициализировано.")
    }

    private fun normalizeBranchingName(name: String?): String? =
        name?.trim()?.takeIf { it.isNotEmpty() }

    private fun normalizeRequiredBranchingName(name: String, entityName: String): String =
        normalizeBranchingName(name) ?: error("Имя $entityName не может быть пустым.")
}


