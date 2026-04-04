package agent.memory.prompt

import agent.memory.model.LongTermMemory
import agent.memory.model.MemoryNote
import agent.memory.model.WorkingMemory
import kotlin.test.Test
import kotlin.test.assertEquals
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

class LayeredMemoryPromptAssemblerTest {
    private val assembler = LayeredMemoryPromptAssembler()

    @Test
    fun `injects layered memory into first system message`() {
        val prompt = assembler.assemble(
            systemPrompt = "Ты помощник.",
            longTermMemory = LongTermMemory(
                notes = listOf(MemoryNote("communication_style", "Отвечай кратко"))
            ),
            workingMemory = WorkingMemory(
                notes = listOf(MemoryNote("goal", "Собрать ТЗ"))
            ),
            shortTermContext = listOf(
                ChatMessage(ChatRole.SYSTEM, "старый системный prompt"),
                ChatMessage(ChatRole.USER, "Привет")
            )
        )

        assertEquals(
            ChatMessage(
                ChatRole.SYSTEM,
                "Ты помощник.\n\nLong-term memory\n- communication_style: Отвечай кратко\n\nWorking memory\n- goal: Собрать ТЗ"
            ),
            prompt.first()
        )
        assertEquals(ChatMessage(ChatRole.USER, "Привет"), prompt[1])
    }

    @Test
    fun `prepends system message when short-term context has none`() {
        val prompt = assembler.assemble(
            systemPrompt = "Ты помощник.",
            longTermMemory = LongTermMemory(),
            workingMemory = WorkingMemory(),
            shortTermContext = listOf(ChatMessage(ChatRole.USER, "Привет"))
        )

        assertEquals(ChatMessage(ChatRole.SYSTEM, "Ты помощник."), prompt.first())
        assertEquals(ChatMessage(ChatRole.USER, "Привет"), prompt[1])
    }
}
