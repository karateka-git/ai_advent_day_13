package agent.memory.persistence

import agent.memory.model.LongTermMemory
import agent.memory.model.MemoryNote
import agent.memory.model.MemoryState
import agent.memory.model.ShortTermMemory
import agent.memory.model.WorkingMemory
import agent.storage.JsonConversationStore
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

class JsonMemoryStateRepositoryTest {
    @Test
    fun `save and load preserve runtime memory state`() {
        val tempDir = Files.createTempDirectory("memory-state-repository-test")
        val repository = JsonMemoryStateRepository(
            conversationStore = JsonConversationStore(tempDir.resolve("conversation.json"))
        )
        val state = MemoryState(
            shortTerm = ShortTermMemory(
                messages = listOf(
                    ChatMessage(ChatRole.SYSTEM, "system"),
                    ChatMessage(ChatRole.USER, "Привет")
                )
            ),
            working = WorkingMemory(
                notes = listOf(MemoryNote(category = "goal", content = "Собрать ТЗ"))
            ),
            longTerm = LongTermMemory(
                notes = listOf(MemoryNote(category = "communication_style", content = "Отвечай кратко"))
            )
        )

        repository.save(state)

        assertEquals(state, repository.load())
    }

    @Test
    fun `loadFrom imports runtime memory state from explicit path`() {
        val tempDir = Files.createTempDirectory("memory-state-repository-test")
        val sourcePath = tempDir.resolve("import.json")
        sourcePath.writeText(
            """
            {
              "shortTerm": {
                "messages": [
                  { "role": "system", "content": "system" },
                  { "role": "user", "content": "Привет" }
                ]
              },
              "working": {
                "notes": [
                  { "category": "goal", "content": "Собрать ТЗ" }
                ]
              },
              "longTerm": {
                "notes": [
                  { "category": "communication_style", "content": "Отвечай кратко" }
                ]
              }
            }
            """.trimIndent()
        )
        val repository = JsonMemoryStateRepository(
            conversationStore = JsonConversationStore(tempDir.resolve("conversation.json"))
        )

        val importedState = repository.loadFrom(sourcePath)

        assertEquals(
            MemoryState(
                shortTerm = ShortTermMemory(
                    messages = listOf(
                        ChatMessage(ChatRole.SYSTEM, "system"),
                        ChatMessage(ChatRole.USER, "Привет")
                    )
                ),
                working = WorkingMemory(
                    notes = listOf(MemoryNote(category = "goal", content = "Собрать ТЗ"))
                ),
                longTerm = LongTermMemory(
                    notes = listOf(MemoryNote(category = "communication_style", content = "Отвечай кратко"))
                )
            ),
            importedState
        )
    }
}
