package agent.task.core

import agent.task.model.ExpectedAction
import agent.task.model.TaskItem
import agent.task.model.TaskSessionState
import agent.task.model.TaskStage
import agent.task.model.TaskStatus
import agent.task.model.TaskStages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DefaultTaskManagerTest {
    @Test
    fun `starts task with default state`() {
        val manager = DefaultTaskManager()

        val task = manager.startTask("Реализовать task subsystem")

        assertEquals("Реализовать task subsystem", task.title)
        assertEquals(TaskStage.PLANNING, task.stage)
        assertEquals(TaskStatus.ACTIVE, task.status)
        assertEquals(ExpectedAction.NONE, task.expectedAction)
        assertNull(task.currentStep)
    }

    @Test
    fun `updates current task fields`() {
        val manager = DefaultTaskManager()
        manager.startTask("Реализовать task subsystem")

        manager.updateStage(TaskStage.EXECUTION)
        manager.updateStep("Добавить доменные модели")
        val task = manager.updateExpectedAction(ExpectedAction.AGENT_EXECUTION)

        assertEquals(TaskStage.EXECUTION, task.stage)
        assertEquals("Добавить доменные модели", task.currentStep)
        assertEquals(ExpectedAction.AGENT_EXECUTION, task.expectedAction)
        assertEquals(TaskStatus.ACTIVE, task.status)
    }

    @Test
    fun `pauses resumes and completes current task`() {
        val manager = DefaultTaskManager()
        manager.startTask("Реализовать task subsystem")

        val pausedTask = manager.pauseTask()
        assertEquals(TaskStatus.PAUSED, pausedTask.status)
        assertNull(manager.activeTask())
        assertEquals(TaskStatus.PAUSED, manager.currentTask()?.status)
        assertNull(manager.sessionState().activeTaskId)

        val resumedTask = manager.resumeTask()
        assertEquals(TaskStatus.ACTIVE, resumedTask.status)
        assertEquals("task-1", manager.sessionState().activeTaskId)

        val completedTask = manager.completeTask()
        assertEquals(TaskStatus.DONE, completedTask.status)
        assertNull(manager.activeTask())
        assertNull(manager.sessionState().activeTaskId)
        assertEquals(TaskStatus.DONE, manager.currentTask()?.status)
    }

    @Test
    fun `fails to update task before start`() {
        val manager = DefaultTaskManager()

        val error = assertFailsWith<IllegalStateException> {
            manager.updateStage(TaskStage.VALIDATION)
        }

        assertEquals("Текущая задача ещё не создана.", error.message)
    }

    @Test
    fun `fails to pause completed task`() {
        val manager = DefaultTaskManager()
        manager.startTask("Реализовать task subsystem")
        manager.completeTask()

        val error = assertFailsWith<IllegalStateException> {
            manager.pauseTask()
        }

        assertEquals("Нет активной задачи для паузы.", error.message)
    }

    @Test
    fun `clears current task`() {
        val manager = DefaultTaskManager()
        manager.startTask("Реализовать task subsystem")

        manager.clearTask()

        assertNull(manager.currentTask())
        assertNull(manager.sessionState().activeTaskId)
        assertEquals(emptyList(), manager.sessionState().tasks)
    }

    @Test
    fun `switches tasks by id and preserves paused tasks`() {
        val manager = DefaultTaskManager()
        manager.startTask("Первая задача")
        manager.startTask("Вторая задача")

        val switchedTask = manager.switchTask("task-1")
        val sessionState = manager.sessionState()

        assertEquals("Первая задача", switchedTask.title)
        assertEquals("task-1", sessionState.activeTaskId)
        assertEquals(TaskStatus.ACTIVE, sessionState.task("task-1")?.status)
        assertEquals(TaskStatus.PAUSED, sessionState.task("task-2")?.status)
        assertEquals("Первая задача", manager.activeTask()?.title)
    }

    @Test
    fun `resumes paused task by id`() {
        val manager = DefaultTaskManager()
        manager.startTask("Первая задача")
        manager.pauseTask()

        val resumedTask = manager.resumeTask("task-1")

        assertEquals(TaskStatus.ACTIVE, resumedTask.status)
        assertEquals("task-1", manager.sessionState().activeTaskId)
        assertEquals(TaskStatus.ACTIVE, manager.sessionState().task("task-1")?.status)
    }

    @Test
    fun `completes task by id and clears active task`() {
        val manager = DefaultTaskManager()
        manager.startTask("Первая задача")

        val completedTask = manager.completeTask("task-1")

        assertEquals(TaskStatus.DONE, completedTask.status)
        assertNull(manager.activeTask())
        assertNull(manager.sessionState().activeTaskId)
        assertEquals(TaskStatus.DONE, manager.sessionState().task("task-1")?.status)
    }

    @Test
    fun `session state enforces unique task ids and active task consistency`() {
        assertFailsWith<IllegalArgumentException> {
            TaskSessionState(
                tasks = listOf(
                    TaskItem(id = "task-1", title = "Первая задача"),
                    TaskItem(id = "task-1", title = "Дублирующая задача")
                )
            )
        }

        assertFailsWith<IllegalArgumentException> {
            TaskSessionState(
                tasks = listOf(
                    TaskItem(id = "task-1", title = "Первая задача", status = TaskStatus.PAUSED)
                ),
                activeTaskId = "task-1"
            )
        }

        assertFailsWith<IllegalArgumentException> {
            TaskSessionState(
                tasks = listOf(
                    TaskItem(id = "task-1", title = "Первая задача", status = TaskStatus.ACTIVE)
                )
            )
        }

        assertFailsWith<IllegalArgumentException> {
            TaskSessionState(
                tasks = listOf(
                    TaskItem(id = "task-1", title = "Первая задача", status = TaskStatus.ACTIVE),
                    TaskItem(id = "task-2", title = "Вторая задача", status = TaskStatus.ACTIVE)
                ),
                activeTaskId = "task-1"
            )
        }
    }

    @Test
    fun `task stages expose metadata for each default stage`() {
        val definitions = TaskStages.all()

        assertEquals(4, definitions.size)
        assertEquals("Планирование", TaskStages.definitionFor(TaskStage.PLANNING).label)
        assertEquals("Завершение", TaskStages.definitionFor(TaskStage.COMPLETION).label)
    }

    @Test
    fun `returns task prompt contribution via manager`() {
        val manager = DefaultTaskManager()
        manager.startTask("Реализовать task subsystem")
        manager.updateStage(TaskStage.VALIDATION)
        manager.updateStep("Проверить CLI-команды")
        manager.updateExpectedAction(ExpectedAction.USER_CONFIRMATION)

        val promptContext = manager.promptContext()

        assertEquals(
            """
            Состояние задачи
            - Название: Реализовать task subsystem
            - Этап: Проверка
            - Статус: активна
            - Ожидаемое действие: подтверждение пользователя
            - Описание этапа: Проверка результата и поиск недочётов
            - Текущий шаг: Проверить CLI-команды
            """.trimIndent(),
            promptContext.systemPromptContribution
        )
    }

    @Test
    fun `returns empty task prompt contribution when task is paused`() {
        val manager = DefaultTaskManager()
        manager.startTask("Реализовать task subsystem")
        manager.pauseTask()

        assertNull(manager.promptContext().systemPromptContribution)
    }

    @Test
    fun `keeps compatible current task when active task is absent`() {
        val manager = DefaultTaskManager()
        manager.startTask("Первая задача")
        manager.pauseTask()

        assertEquals("Первая задача", manager.currentTask()?.title)
        assertEquals(TaskStatus.PAUSED, manager.currentTask()?.status)
        assertNull(manager.activeTask())
        assertNull(manager.promptContext().systemPromptContribution)
    }

    @Test
    fun `returns empty task prompt contribution when task is absent`() {
        val manager = DefaultTaskManager()

        assertNull(manager.promptContext().systemPromptContribution)
    }

    @Test
    fun `exposes session state for active task`() {
        val manager = DefaultTaskManager()

        manager.startTask("Реализовать task subsystem")

        val sessionState = manager.sessionState()

        assertEquals(1, sessionState.tasks.size)
        assertEquals("task-1", sessionState.activeTaskId)
        assertEquals(1, manager.listTasks().size)
        assertEquals("task-1", manager.activeTask()?.id)
        val activeTask = sessionState.activeTask()
        assertNotNull(activeTask)
        assertEquals("Реализовать task subsystem", activeTask.title)
        assertEquals(TaskStatus.ACTIVE, activeTask.status)
    }

    @Test
    fun `starting second task keeps first task and pauses previous active`() {
        val manager = DefaultTaskManager()
        manager.startTask("Первая задача")

        val secondTask = manager.startTask("Вторая задача")
        val sessionState = manager.sessionState()

        assertEquals("Вторая задача", secondTask.title)
        assertEquals(2, sessionState.tasks.size)
        assertEquals("task-2", sessionState.activeTaskId)
        assertEquals(TaskStatus.PAUSED, sessionState.tasks.first { it.id == "task-1" }.status)
        assertEquals(TaskStatus.ACTIVE, sessionState.tasks.first { it.id == "task-2" }.status)
        assertEquals("Вторая задача", manager.currentTask()?.title)
    }

    @Test
    fun `starting new task does not rewrite completed previous task`() {
        val manager = DefaultTaskManager()
        manager.startTask("Первая задача")
        manager.completeTask()

        manager.startTask("Вторая задача")

        val sessionState = manager.sessionState()

        assertEquals(TaskStatus.DONE, sessionState.tasks.first { it.id == "task-1" }.status)
        assertEquals(TaskStatus.ACTIVE, sessionState.tasks.first { it.id == "task-2" }.status)
        assertEquals("task-2", sessionState.activeTaskId)
    }
}
