package agent.storage

import agent.storage.model.ConversationMemoryState
import agent.storage.model.StoredLongTermMemory
import agent.storage.model.StoredMemoryNote
import agent.storage.model.StoredMessage
import agent.storage.model.StoredShortTermMemory
import agent.storage.model.StoredSummary
import agent.storage.model.StoredSummaryStrategyState
import agent.storage.model.StoredWorkingMemory
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class JsonConversationStoreTest {
    @Test
    fun `loadState returns empty state when file does not exist`() {
        val tempDir = Files.createTempDirectory("conversation-store-test")
        val store = JsonConversationStore(tempDir.resolve("conversation.json"))

        assertEquals(ConversationMemoryState(), store.loadState())
    }

    @Test
    fun `loadState rejects legacy conversation history format`() {
        val tempDir = Files.createTempDirectory("conversation-store-test")
        val storagePath = tempDir.resolve("conversation.json")
        storagePath.writeText(
            """
            {
              "messages": [
                { "role": "system", "content": "Ты помощник." },
                { "role": "user", "content": "Привет" }
              ]
            }
            """.trimIndent()
        )
        val store = JsonConversationStore(storagePath)

        assertFails {
            store.loadState()
        }
    }

    @Test
    fun `saveState and loadState preserve short-term messages`() {
        val tempDir = Files.createTempDirectory("conversation-store-test")
        val store = JsonConversationStore(tempDir.resolve("conversation.json"))
        val state = ConversationMemoryState(
            shortTerm = StoredShortTermMemory(
                messages = listOf(
                    StoredMessage(role = "system", content = "Ты помощник."),
                    StoredMessage(role = "user", content = "Привет"),
                    StoredMessage(role = "assistant", content = "Здравствуйте")
                )
            )
        )

        store.saveState(state)

        assertEquals(state, store.loadState())
    }

    @Test
    fun `saveState and loadState preserve layered memory`() {
        val tempDir = Files.createTempDirectory("conversation-store-test")
        val store = JsonConversationStore(tempDir.resolve("conversation.json"))
        val state = ConversationMemoryState(
            shortTerm = StoredShortTermMemory(
                messages = listOf(
                    StoredMessage(role = "system", content = "Ты помощник."),
                    StoredMessage(role = "user", content = "Привет")
                ),
                strategyState = StoredSummaryStrategyState(
                    summary = StoredSummary(
                        content = "Пользователь поздоровался.",
                        coveredMessagesCount = 2
                    ),
                    coveredMessagesCount = 2
                )
            ),
            working = StoredWorkingMemory(
                notes = listOf(StoredMemoryNote(category = "goal", content = "Собрать ТЗ"))
            ),
            longTerm = StoredLongTermMemory(
                notes = listOf(StoredMemoryNote(category = "communication_style", content = "Отвечай кратко"))
            )
        )

        store.saveState(state)

        assertEquals(state, store.loadState())
    }
}
