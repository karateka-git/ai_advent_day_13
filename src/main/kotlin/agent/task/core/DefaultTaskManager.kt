package agent.task.core

import agent.task.model.ExpectedAction
import agent.task.model.TaskItem
import agent.task.model.TaskSessionState
import agent.task.model.TaskStage
import agent.task.model.TaskState
import agent.task.model.TaskStatus
import agent.task.model.TaskStages
import agent.task.persistence.TaskSessionStateRepository
import agent.task.prompt.TaskPromptContext

/**
 * In-memory реализация [TaskManager].
 *
 * Хранит полное task session state, управляет переключением активной задачи и поддерживает
 * совместимый single-task view для мест, которые работают с текущей сфокусированной задачей.
 */
class DefaultTaskManager(
    initialTask: TaskState? = null,
    private val repository: TaskSessionStateRepository? = null
) : TaskManager {
    private var session: TaskSessionState = initialTask
        ?.let(::sessionForSingleTask)
        ?: repository?.load()
        ?: TaskSessionState()

    override fun sessionState(): TaskSessionState = session

    override fun listTasks(): List<TaskItem> = session.tasks

    override fun activeTask(): TaskItem? = session.activeTask()

    override fun currentTask(): TaskState? = currentCompatibleTask()?.toTaskState()

    override fun promptContext(): TaskPromptContext =
        TaskPromptContext(
            systemPromptContribution = activeTask()?.toTaskState()?.let(::buildPromptContribution)
        )

    override fun startTask(title: String): TaskState {
        require(title.isNotBlank()) { "Название задачи не должно быть пустым." }

        val currentActiveTask = activeTask()
        val previousTasks = session.tasks.map { task ->
            if (currentActiveTask != null && task.id == currentActiveTask.id && task.status != TaskStatus.DONE) {
                task.copy(status = TaskStatus.PAUSED)
            } else {
                task
            }
        }
        val newTask = TaskItem.fromTaskState(nextTaskId(previousTasks), TaskState(title = title.trim()))

        session = TaskSessionState(
            tasks = previousTasks + newTask,
            activeTaskId = newTask.id
        )
        persistSession()

        return newTask.toTaskState()
    }

    override fun updateStage(stage: TaskStage): TaskState =
        updateCurrentTask { task -> task.copy(stage = stage) }

    override fun updateStep(step: String): TaskState {
        require(step.isNotBlank()) { "Текущий шаг не должен быть пустым." }
        return updateCurrentTask { task -> task.copy(currentStep = step.trim()) }
    }

    override fun updateExpectedAction(action: ExpectedAction): TaskState =
        updateCurrentTask { task -> task.copy(expectedAction = action) }

    override fun pauseTask(): TaskState =
        pauseCurrentTask()

    override fun resumeTask(): TaskState =
        resumeCurrentOrLastPausedTask()

    override fun switchTask(taskId: String): TaskState = activateTask(taskId)

    override fun resumeTask(taskId: String): TaskState = resumeTaskById(taskId)

    override fun completeTask(): TaskState =
        activeTask()?.let { task -> completeTask(task.id) } ?: error("Нет активной задачи для завершения.")

    override fun completeTask(taskId: String): TaskState {
        val targetTask = requireTask(taskId)
        require(targetTask.status != TaskStatus.DONE) {
            "Задача '$taskId' уже завершена."
        }

        return updateTaskState(taskId) { task -> task.copy(status = TaskStatus.DONE) }
    }

    override fun clearTask() {
        session = TaskSessionState()
        repository?.clear()
    }

    /**
     * Применяет изменение к активной задаче и сохраняет обновлённое состояние.
     */
    private fun updateCurrentTask(transform: (TaskState) -> TaskState): TaskState {
        val activeTask = activeTask() ?: error("Текущая задача ещё не создана.")
        val updatedTask = TaskItem.fromTaskState(activeTask.id, transform(activeTask.toTaskState()))

        session = TaskSessionState(
            tasks = session.tasks.map { task ->
                if (task.id == activeTask.id) updatedTask else task
            },
            activeTaskId = activeTask.id
        )
        persistSession()

        return updatedTask.toTaskState()
    }

    /**
     * Ставит текущую активную задачу на паузу и снимает active state.
     */
    private fun pauseCurrentTask(): TaskState {
        val activeTask = activeTask() ?: error("Нет активной задачи для паузы.")
        require(activeTask.status != TaskStatus.DONE) {
            "Нельзя поставить на паузу уже завершённую задачу."
        }

        val updatedTask = activeTask.copy(status = TaskStatus.PAUSED)
        session = TaskSessionState(
            tasks = session.tasks.map { task ->
                if (task.id == activeTask.id) updatedTask else task
            },
            activeTaskId = null
        )
        persistSession()

        return updatedTask.toTaskState()
    }

    /**
     * Делает указанную задачу активной и, если нужно, переводит предыдущую активную задачу в pause.
     */
    private fun activateTask(taskId: String): TaskState {
        val targetTask = requireTask(taskId)
        require(targetTask.status != TaskStatus.DONE) {
            "Нельзя переключиться на завершённую задачу."
        }
        if (session.activeTaskId == taskId && targetTask.status == TaskStatus.ACTIVE) {
            return targetTask.toTaskState()
        }

        val currentActiveTaskId = session.activeTaskId
        val updatedTasks = session.tasks.map { task ->
            when (task.id) {
                taskId -> task.copy(status = TaskStatus.ACTIVE)
                currentActiveTaskId -> if (task.status == TaskStatus.ACTIVE) task.copy(status = TaskStatus.PAUSED) else task
                else -> task
            }
        }

        session = TaskSessionState(
            tasks = updatedTasks,
            activeTaskId = taskId
        )
        persistSession()

        return session.task(taskId)?.toTaskState() ?: error("Задача '$taskId' не найдена.")
    }

    /**
     * Возобновляет только paused-задачу, сохраняя смысл resume как восстановления рабочей задачи.
     */
    private fun resumeTaskById(taskId: String): TaskState {
        val targetTask = requireTask(taskId)
        require(targetTask.status != TaskStatus.DONE) {
            "Нельзя возобновить завершённую задачу."
        }
        if (session.activeTaskId == taskId && targetTask.status == TaskStatus.ACTIVE) {
            return targetTask.toTaskState()
        }
        require(targetTask.status == TaskStatus.PAUSED) {
            "Можно возобновить только приостановленную задачу."
        }

        return activateTask(taskId)
    }

    /**
     * Применяет изменение к задаче и, при необходимости, пересчитывает activeTaskId.
     */
    private fun updateTaskState(taskId: String, transform: (TaskItem) -> TaskItem): TaskState {
        val existingTask = requireTask(taskId)
        val updatedTask = transform(existingTask)
        val newActiveTaskId = if (updatedTask.status == TaskStatus.ACTIVE) taskId else {
            if (session.activeTaskId == taskId) null else session.activeTaskId
        }

        session = TaskSessionState(
            tasks = session.tasks.map { task ->
                if (task.id == taskId) updatedTask else task
            },
            activeTaskId = newActiveTaskId
        )
        persistSession()

        return updatedTask.toTaskState()
    }

    /**
     * Возвращает задачу по id или завершает работу с понятной ошибкой.
     */
    private fun requireTask(taskId: String): TaskItem =
        session.task(taskId) ?: error("Задача '$taskId' не найдена.")

    /**
     * Возвращает наиболее позднюю paused-задачу, если активной сейчас нет.
     */
    private fun lastPausedTask(): TaskItem? =
        session.tasks.lastOrNull { it.status == TaskStatus.PAUSED }

    /**
     * Возвращает задачу для совместимого single-task view: active task, если она есть, иначе
     * последний сохранённый рабочий трек.
     */
    private fun currentCompatibleTask(): TaskItem? =
        activeTask() ?: session.tasks.lastOrNull()

    /**
     * Возобновляет последнюю paused-задачу, если активной задачи сейчас нет.
     */
    private fun resumeCurrentOrLastPausedTask(): TaskState {
        activeTask()?.let { return it.toTaskState() }
        val pausedTask = lastPausedTask() ?: error("Нет приостановленной задачи для возобновления.")
        return activateTask(pausedTask.id)
    }

    /**
     * Сохраняет актуальный task session state.
     */
    private fun persistSession() {
        repository?.save(session)
    }

    /**
     * Формирует task-derived contribution для итогового system prompt.
     */
    private fun buildPromptContribution(taskState: TaskState): String {
        val stageDefinition = TaskStages.definitionFor(taskState.stage)
        return buildString {
            appendLine("Состояние задачи")
            appendLine("- Название: ${taskState.title}")
            appendLine("- Этап: ${stageDefinition.label}")
            appendLine("- Статус: ${statusLabel(taskState.status)}")
            appendLine("- Ожидаемое действие: ${expectedActionLabel(taskState.expectedAction)}")
            appendLine("- Описание этапа: ${stageDefinition.description}")
            append("- Текущий шаг: ${taskState.currentStep ?: "(не задан)"}")
        }
    }

    private fun statusLabel(status: TaskStatus): String =
        when (status) {
            TaskStatus.ACTIVE -> "активна"
            TaskStatus.PAUSED -> "на паузе"
            TaskStatus.DONE -> "завершена"
        }

    private fun expectedActionLabel(action: ExpectedAction): String =
        when (action) {
            ExpectedAction.USER_INPUT -> "ввод пользователя"
            ExpectedAction.AGENT_EXECUTION -> "выполнение агентом"
            ExpectedAction.USER_CONFIRMATION -> "подтверждение пользователя"
            ExpectedAction.NONE -> "не задано"
        }

    private fun sessionForSingleTask(taskState: TaskState): TaskSessionState =
        TaskSessionState(
            tasks = listOf(TaskItem.fromTaskState(DEFAULT_ACTIVE_TASK_ID, taskState)),
            activeTaskId = taskState.status
                .takeIf { it == TaskStatus.ACTIVE }
                ?.let { DEFAULT_ACTIVE_TASK_ID }
        )

    private fun nextTaskId(tasks: List<TaskItem>): String {
        val nextIndex = tasks
            .mapNotNull { task -> task.id.removePrefix(TASK_ID_PREFIX).toIntOrNull() }
            .maxOrNull()
            ?.plus(1)
            ?: 1

        return "$TASK_ID_PREFIX$nextIndex"
    }

    companion object {
        private const val DEFAULT_ACTIVE_TASK_ID = "task-1"
        private const val TASK_ID_PREFIX = "task-"
    }
}
