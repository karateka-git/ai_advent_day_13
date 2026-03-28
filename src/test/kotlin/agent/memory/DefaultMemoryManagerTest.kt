package agent.memory

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
    fun `initializes storage with system message when history is empty`() {
        val tempDir = Files.createTempDirectory("memory-manager-test")
        val store = JsonConversationStore(tempDir.resolve("conversation.json"))

        val manager = DefaultMemoryManager(
            languageModel = FakeLanguageModel(),
            systemPrompt = "Системное сообщение",
            conversationStore = store
        )

        assertEquals(
            listOf(ChatMessage(role = ChatRole.SYSTEM, content = "Системное сообщение")),
            manager.currentConversation()
        )
    }

    @Test
    fun `appendUserMessage returns updated conversation and persists it`() {
        val tempDir = Files.createTempDirectory("memory-manager-test")
        val store = JsonConversationStore(tempDir.resolve("conversation.json"))
        val manager = DefaultMemoryManager(
            languageModel = FakeLanguageModel(),
            systemPrompt = "Системное сообщение",
            conversationStore = store
        )

        val conversation = manager.appendUserMessage("Привет")

        assertEquals(
            listOf(
                ChatMessage(role = ChatRole.SYSTEM, content = "Системное сообщение"),
                ChatMessage(role = ChatRole.USER, content = "Привет")
            ),
            conversation
        )
        assertEquals(
            conversation,
            DefaultMemoryManager(
                languageModel = FakeLanguageModel(),
                systemPrompt = "Системное сообщение",
                conversationStore = store
            ).currentConversation()
        )
    }

    @Test
    fun `clear keeps only system message`() {
        val tempDir = Files.createTempDirectory("memory-manager-test")
        val store = JsonConversationStore(tempDir.resolve("conversation.json"))
        val manager = DefaultMemoryManager(
            languageModel = FakeLanguageModel(),
            systemPrompt = "Системное сообщение",
            conversationStore = store
        )

        manager.appendUserMessage("Привет")
        manager.appendAssistantMessage("Здравствуйте")
        manager.clear()

        assertEquals(
            listOf(ChatMessage(role = ChatRole.SYSTEM, content = "Системное сообщение")),
            manager.currentConversation()
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
        error("РќРµ РґРѕР»Р¶РµРЅ РІС‹Р·С‹РІР°С‚СЊСЃСЏ РІ СЌС‚РѕРј С‚РµСЃС‚Рµ.")
}
