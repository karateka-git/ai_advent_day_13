package agent.task.prompt

import agent.task.model.ExpectedAction
import agent.task.model.TaskStage
import agent.task.model.TaskState
import agent.task.model.TaskStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TaskPromptAssemblerTest {
    private val assembler = TaskPromptAssembler()

    @Test
    fun `returns null when task is absent`() {
        assertNull(assembler.assemble(null))
    }

    @Test
    fun `formats compact task block`() {
        val block = assembler.assemble(
            TaskState(
                title = "Реализовать task subsystem",
                stage = TaskStage.VALIDATION,
                currentStep = "Проверить CLI-команды",
                expectedAction = ExpectedAction.USER_CONFIRMATION,
                status = TaskStatus.PAUSED
            )
        )

        assertEquals(
            """
            Task state
            - Title: Реализовать task subsystem
            - Stage: Проверка
            - Status: paused
            - Expected action: user_confirmation
            - Stage details: Проверка результата и поиск недочётов
            - Current step: Проверить CLI-команды
            """.trimIndent(),
            block
        )
    }

    @Test
    fun `enriches system prompt when task exists`() {
        val prompt = assembler.enrichSystemPrompt(
            systemPrompt = "Ты помощник.",
            taskState = TaskState(title = "Собрать roadmap")
        )

        assertEquals(
            """
            Ты помощник.

            Task state
            - Title: Собрать roadmap
            - Stage: Планирование
            - Status: active
            - Expected action: none
            - Stage details: Уточнение задачи, подхода и ближайших шагов
            - Current step: (not specified)
            """.trimIndent(),
            prompt
        )
    }
}
