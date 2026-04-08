package agent.memory

import agent.memory.core.DefaultMemoryManager
import agent.memory.core.MemoryStrategy
import agent.memory.layer.MemoryLayerAllocator
import agent.memory.model.MemoryCandidateDraft
import agent.memory.model.MemoryLayer
import agent.memory.model.MemoryState
import agent.memory.persistence.JsonMemoryStateRepository
import agent.memory.strategy.nocompression.NoCompressionMemoryStrategy
import agent.memory.strategy.slidingwindow.SlidingWindowMemoryStrategy
import agent.storage.JsonConversationStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import llm.core.LanguageModel
import llm.core.model.ChatMessage
import llm.core.model.ChatRole
import llm.core.model.LanguageModelInfo
import llm.core.model.LanguageModelResponse

class DefaultMemoryManagerTest {
    @Test
    fun `initializes storage with empty runtime history when history is empty`() {
        val manager = createManager()

        assertEquals(emptyList(), manager.currentConversation())
    }

    @Test
    fun `puts long-term candidates into pending instead of immediate durable memory`() {
        val manager = createManager(
            allocator = object : MemoryLayerAllocator {
                override fun extractCandidates(state: MemoryState, message: ChatMessage): List<MemoryCandidateDraft> =
                    listOf(
                        MemoryCandidateDraft(
                            targetLayer = MemoryLayer.LONG_TERM,
                            category = "communication_style",
                            content = "Отвечай кратко"
                        )
                    )
            }
        )

        manager.appendUserMessage("Отвечай кратко")

        assertEquals(emptyList(), manager.memoryState().longTerm.notes)
        assertEquals(1, manager.pendingMemory().candidates.size)
        assertEquals("communication_style", manager.pendingMemory().candidates.single().category)
    }

    @Test
    fun `approves pending candidate into durable memory`() {
        val manager = createManager(
            allocator = object : MemoryLayerAllocator {
                override fun extractCandidates(state: MemoryState, message: ChatMessage): List<MemoryCandidateDraft> =
                    listOf(
                        MemoryCandidateDraft(
                            targetLayer = MemoryLayer.LONG_TERM,
                            category = "communication_style",
                            content = "Отвечай кратко"
                        )
                    )
            }
        )

        manager.appendUserMessage("Отвечай кратко")
        val pendingId = manager.pendingMemory().candidates.single().id
        manager.approvePendingMemory(listOf(pendingId))

        assertEquals(emptyList(), manager.pendingMemory().candidates)
        assertEquals("communication_style", manager.memoryState().longTerm.notes.single().category)
    }

    @Test
    fun `preview prompt context does not invoke memory layer allocator`() {
        val manager = createManager(
            allocator = object : MemoryLayerAllocator {
                override fun extractCandidates(state: MemoryState, message: ChatMessage): List<MemoryCandidateDraft> =
                    error("Allocator не должен вызываться в previewPromptContext.")
            }
        )

        manager.previewPromptContext("Цель задачи - сделать MVP")
    }

    @Test
    fun `rebuilds derived short-term context from raw journal when strategy changes`() {
        val tempDir = Files.createTempDirectory("memory-manager-test")
        val repository = JsonMemoryStateRepository(JsonConversationStore(tempDir.resolve("conversation.json")))
        val noCompressionManager = createManager(
            repository = repository,
            strategy = NoCompressionMemoryStrategy()
        )

        noCompressionManager.appendUserMessage("u1")
        noCompressionManager.appendAssistantMessage("a1")
        noCompressionManager.appendUserMessage("u2")

        val slidingWindowManager = createManager(
            repository = repository,
            strategy = SlidingWindowMemoryStrategy(recentMessagesCount = 2)
        )
        val shortTerm = slidingWindowManager.memoryState().shortTerm

        assertEquals(
            listOf(
                ChatMessage(role = ChatRole.USER, content = "u1"),
                ChatMessage(role = ChatRole.ASSISTANT, content = "a1"),
                ChatMessage(role = ChatRole.USER, content = "u2")
            ),
            shortTerm.rawMessages
        )
        assertEquals(
            listOf(
                ChatMessage(role = ChatRole.ASSISTANT, content = "a1"),
                ChatMessage(role = ChatRole.USER, content = "u2")
            ),
            shortTerm.derivedMessages
        )
    }

    private fun createManager(
        repository: JsonMemoryStateRepository? = null,
        strategy: MemoryStrategy = NoCompressionMemoryStrategy(),
        allocator: MemoryLayerAllocator = object : MemoryLayerAllocator {
            override fun extractCandidates(state: MemoryState, message: ChatMessage): List<MemoryCandidateDraft> = emptyList()
        }
    ): DefaultMemoryManager {
        val effectiveRepository = repository ?: run {
            val tempDir = Files.createTempDirectory("memory-manager-test")
            JsonMemoryStateRepository(JsonConversationStore(tempDir.resolve("conversation.json")))
        }

        return DefaultMemoryManager(
            languageModel = FakeLanguageModel(),
            memoryStateRepository = effectiveRepository,
            memoryStrategy = strategy,
            memoryLayerAllocator = allocator
        )
    }
}

private class FakeLanguageModel : LanguageModel {
    override val info = LanguageModelInfo(
        name = "FakeLanguageModel",
        model = "fake-model"
    )

    override val tokenCounter = null

    override fun complete(messages: List<ChatMessage>): LanguageModelResponse =
        error("Не должен вызываться в этом тесте.")
}
