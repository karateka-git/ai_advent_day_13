package agent.task.core

import agent.task.model.ExpectedAction
import agent.task.model.TaskItem
import agent.task.model.TaskSessionState
import agent.task.model.TaskStage
import agent.task.model.TaskStatus
import agent.task.persistence.TaskSessionStateRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DefaultTaskManagerPersistenceTest {
    @Test
    fun `loads current task from repository on startup`() {
        val repository = InMemoryTaskSessionStateRepository(
            initialState = TaskSessionState(
                tasks = listOf(
                    TaskItem(
                        id = "task-1",
                        title = "Реализовать task subsystem",
                        stage = TaskStage.EXECUTION,
                        currentStep = "Добавить persistence",
                        expectedAction = ExpectedAction.AGENT_EXECUTION,
                        status = TaskStatus.ACTIVE
                    )
                ),
                activeTaskId = "task-1"
            )
        )

        val manager = DefaultTaskManager(repository = repository)

        assertEquals("Реализовать task subsystem", manager.currentTask()?.title)
        assertEquals(repository.state, manager.sessionState())
    }

    @Test
    fun `persists task session updates through repository`() {
        val repository = InMemoryTaskSessionStateRepository()
        val manager = DefaultTaskManager(repository = repository)

        manager.startTask("Реализовать task subsystem")
        manager.updateStage(TaskStage.COMPLETION)
        manager.updateExpectedAction(ExpectedAction.USER_CONFIRMATION)
        manager.pauseTask()

        assertEquals(manager.sessionState(), repository.state)
    }

    @Test
    fun `clears repository when task is cleared`() {
        val repository = InMemoryTaskSessionStateRepository(
            initialState = TaskSessionState(
                tasks = listOf(TaskItem(id = "task-1", title = "Реализовать task subsystem")),
                activeTaskId = "task-1"
            )
        )
        val manager = DefaultTaskManager(repository = repository)

        manager.clearTask()

        assertNull(manager.currentTask())
        assertNull(repository.state)
    }
}

private class InMemoryTaskSessionStateRepository(
    initialState: TaskSessionState? = null
) : TaskSessionStateRepository {
    var state: TaskSessionState? = initialState

    override fun load(): TaskSessionState? = state

    override fun save(state: TaskSessionState) {
        this.state = state
    }

    override fun clear() {
        state = null
    }
}
