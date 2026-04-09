package agent.task.core

import agent.task.model.ExpectedAction
import agent.task.model.TaskStage
import agent.task.model.TaskState
import agent.task.model.TaskStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultTaskOrchestrationServiceTest {
    private val service = DefaultTaskOrchestrationService()

    @Test
    fun `returns none when there is no task`() {
        val decision = service.evaluate(null)

        assertEquals(TaskGuardDecision.None, decision)
    }

    @Test
    fun `blocks paused task`() {
        val decision = service.evaluate(sampleTask(status = TaskStatus.PAUSED))

        val block = assertIs<TaskGuardDecision.Block>(decision)
        assertEquals(TaskBlockReason.PAUSED, block.reason)
        assertTrue(block.message.contains("на паузе"))
    }

    @Test
    fun `guides user input before stage semantics`() {
        val decision = service.evaluate(
            sampleTask(
                stage = TaskStage.EXECUTION,
                expectedAction = ExpectedAction.USER_INPUT
            )
        )

        val guide = assertIs<TaskGuardDecision.Guide>(decision)
        assertEquals(TaskBehaviorMode.EXECUTION, guide.mode)
        assertEquals(ExpectedAction.USER_INPUT, guide.expectedAction)
        assertTrue(guide.guidance.contains("нужен содержательный ввод пользователя"))
    }

    @Test
    fun `guides planning stage`() {
        val decision = service.evaluate(sampleTask(stage = TaskStage.PLANNING))

        val guide = assertIs<TaskGuardDecision.Guide>(decision)
        assertEquals(TaskBehaviorMode.PLANNING, guide.mode)
        assertNull(guide.expectedAction)
    }

    @Test
    fun `guides validation stage`() {
        val decision = service.evaluate(sampleTask(stage = TaskStage.VALIDATION))

        val guide = assertIs<TaskGuardDecision.Guide>(decision)
        assertEquals(TaskBehaviorMode.VALIDATION, guide.mode)
    }

    @Test
    fun `guides completion stage`() {
        val decision = service.evaluate(sampleTask(stage = TaskStage.COMPLETION))

        val guide = assertIs<TaskGuardDecision.Guide>(decision)
        assertEquals(TaskBehaviorMode.COMPLETION, guide.mode)
    }

    @Test
    fun `guides done task as completion`() {
        val decision = service.evaluate(sampleTask(status = TaskStatus.DONE))

        val guide = assertIs<TaskGuardDecision.Guide>(decision)
        assertEquals(TaskBehaviorMode.COMPLETION, guide.mode)
    }

    @Test
    fun `returns none for neutral execution state`() {
        val decision = service.evaluate(sampleTask(stage = TaskStage.EXECUTION))

        assertEquals(TaskGuardDecision.None, decision)
    }

    @Test
    fun `guides user confirmation`() {
        val decision = service.evaluate(
            sampleTask(expectedAction = ExpectedAction.USER_CONFIRMATION)
        )

        val guide = assertIs<TaskGuardDecision.Guide>(decision)
        assertEquals(ExpectedAction.USER_CONFIRMATION, guide.expectedAction)
        assertTrue(guide.guidance.contains("подтверждение"))
    }

    private fun sampleTask(
        stage: TaskStage = TaskStage.EXECUTION,
        expectedAction: ExpectedAction = ExpectedAction.NONE,
        status: TaskStatus = TaskStatus.ACTIVE
    ): TaskState = TaskState(
        title = "Task",
        stage = stage,
        currentStep = "Step",
        expectedAction = expectedAction,
        status = status
    )
}
