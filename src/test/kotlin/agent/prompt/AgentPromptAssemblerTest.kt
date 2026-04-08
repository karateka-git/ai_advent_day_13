package agent.prompt

import kotlin.test.Test
import kotlin.test.assertEquals
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

class AgentPromptAssemblerTest {
    private val assembler = AgentPromptAssembler()

    @Test
    fun `assembles final system prompt from base prompt and contributions`() {
        val prompt = assembler.assembleSystemPrompt(
            baseSystemPrompt = "Ты помощник.",
            contributions = listOf("Memory block", "Task block")
        )

        assertEquals(
            """
            Ты помощник.

            Memory block

            Task block
            """.trimIndent(),
            prompt
        )
    }

    @Test
    fun `always prepends final system message to runtime messages`() {
        val conversation = assembler.assembleConversation(
            baseSystemPrompt = "Ты помощник.",
            messages = listOf(
                ChatMessage(ChatRole.SYSTEM, "Synthetic summary"),
                ChatMessage(ChatRole.USER, "Привет")
            ),
            contributions = listOf("Memory block")
        )

        assertEquals(
            ChatMessage(
                ChatRole.SYSTEM,
                """
                Ты помощник.

                Memory block
                """.trimIndent()
            ),
            conversation.first()
        )
        assertEquals(ChatMessage(ChatRole.SYSTEM, "Synthetic summary"), conversation[1])
        assertEquals(ChatMessage(ChatRole.USER, "Привет"), conversation[2])
    }

    @Test
    fun `prepends system message when conversation has no system entries`() {
        val conversation = assembler.assembleConversation(
            baseSystemPrompt = "Ты помощник.",
            messages = listOf(ChatMessage(ChatRole.USER, "Привет")),
            contributions = emptyList()
        )

        assertEquals(ChatMessage(ChatRole.SYSTEM, "Ты помощник."), conversation.first())
        assertEquals(ChatMessage(ChatRole.USER, "Привет"), conversation[1])
    }
}
