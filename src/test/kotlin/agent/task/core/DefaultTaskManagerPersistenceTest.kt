package agent.task.core

import agent.task.model.ExpectedAction
import agent.task.model.TaskStage
import agent.task.model.TaskState
import agent.task.model.TaskStatus
import agent.task.persistence.TaskStateRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DefaultTaskManagerPersistenceTest {
    @Test
    fun `loads current task from repository on startup`() {
        val repository = InMemoryTaskStateRepository(
            initialState = TaskState(
                title = "Реализовать task subsystem",
                stage = TaskStage.EXECUTION,
                currentStep = "Добавить persistence",
                expectedAction = ExpectedAction.AGENT_EXECUTION,
                status = TaskStatus.ACTIVE
            )
        )

        val manager = DefaultTaskManager(repository = repository)

        assertEquals(repository.state, manager.currentTask())
    }

    @Test
    fun `persists task updates through repository`() {
        val repository = InMemoryTaskStateRepository()
        val manager = DefaultTaskManager(repository = repository)

        manager.startTask("Реализовать task subsystem")
        manager.updateStage(TaskStage.COMPLETION)
        manager.updateExpectedAction(ExpectedAction.USER_CONFIRMATION)
        manager.pauseTask()

        assertEquals(manager.currentTask(), repository.state)
    }

    @Test
    fun `clears repository when task is cleared`() {
        val repository = InMemoryTaskStateRepository(
            initialState = TaskState(title = "Реализовать task subsystem")
        )
        val manager = DefaultTaskManager(repository = repository)

        manager.clearTask()

        assertNull(manager.currentTask())
        assertNull(repository.state)
    }
}

private class InMemoryTaskStateRepository(
    initialState: TaskState? = null
) : TaskStateRepository {
    var state: TaskState? = initialState

    override fun load(): TaskState? = state

    override fun save(state: TaskState) {
        this.state = state
    }

    override fun clear() {
        state = null
    }
}
