package agent.task.persistence

import agent.task.model.ExpectedAction
import agent.task.model.TaskItem
import agent.task.model.TaskSessionState
import agent.task.model.TaskStage
import agent.task.model.TaskStatus
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JsonTaskStateRepositoryTest {
    @Test
    fun `loads null when file does not exist`() {
        val tempDir = Files.createTempDirectory("task-state-repo-test")
        val repository = JsonTaskStateRepository(tempDir.resolve("task-state.json"))

        assertNull(repository.load())
    }

    @Test
    fun `saves and loads task session state`() {
        val tempDir = Files.createTempDirectory("task-state-repo-test")
        val repository = JsonTaskStateRepository(tempDir.resolve("task-state.json"))
        val expected = TaskSessionState(
            tasks = listOf(
                TaskItem(
                    id = "task-1",
                    title = "Реализовать task subsystem",
                    stage = TaskStage.VALIDATION,
                    currentStep = "Проверить JSON persistence",
                    expectedAction = ExpectedAction.USER_CONFIRMATION,
                    status = TaskStatus.PAUSED
                )
            ),
            activeTaskId = "task-1"
        )

        repository.save(expected)
        val actual = repository.load()

        assertEquals(expected, actual)
    }

    @Test
    fun `clears persisted task session state`() {
        val tempDir = Files.createTempDirectory("task-state-repo-test")
        val storagePath = tempDir.resolve("task-state.json")
        val repository = JsonTaskStateRepository(storagePath)
        repository.save(
            TaskSessionState(
                tasks = listOf(TaskItem(id = "task-1", title = "Реализовать task subsystem")),
                activeTaskId = "task-1"
            )
        )

        repository.clear()

        assertNull(repository.load())
        assertEquals(false, Files.exists(storagePath))
    }
}
