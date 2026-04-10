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
 * In-memory реализация [TaskManager], которая уже хранит session state, но снаружи пока работает
 * через совместимый single-task API активной задачи.
 *
 * На этапе 3 менеджер ещё не раскрывает полноценный multitask UX, но уже держит явную task session
 * с активной задачей и готов к последующему расширению.
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

    override fun currentTask(): TaskState? = session.activeTask()?.toTaskState()

    override fun promptContext(): TaskPromptContext =
        TaskPromptContext(
            systemPromptContribution = currentTask()?.let(::buildPromptContribution)
        )

    override fun startTask(title: String): TaskState {
        require(title.isNotBlank()) { "Название задачи не должно быть пустым." }

        return TaskState(title = title.trim()).also(::replaceActiveTask)
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
        updateCurrentTask { task ->
            require(task.status != TaskStatus.DONE) {
                "Нельзя поставить на паузу уже завершённую задачу."
            }
            task.copy(status = TaskStatus.PAUSED)
        }

    override fun resumeTask(): TaskState =
        updateCurrentTask { task ->
            require(task.status != TaskStatus.DONE) {
                "Нельзя возобновить уже завершённую задачу."
            }
            task.copy(status = TaskStatus.ACTIVE)
        }

    override fun completeTask(): TaskState =
        updateCurrentTask { task -> task.copy(status = TaskStatus.DONE) }

    override fun clearTask() {
        session = TaskSessionState()
        repository?.clear()
    }

    /**
     * Применяет изменение к активной задаче и сохраняет обновлённое состояние.
     */
    private fun updateCurrentTask(transform: (TaskState) -> TaskState): TaskState {
        val existingTask = currentTask() ?: error("Текущая задача ещё не создана.")
        return transform(existingTask).also(::replaceActiveTask)
    }

    /**
     * Обновляет активную задачу в session state и синхронизирует совместимый single-task persistence.
     */
    private fun replaceActiveTask(taskState: TaskState) {
        val activeId = session.activeTaskId ?: DEFAULT_ACTIVE_TASK_ID
        val activeTask = TaskItem.fromTaskState(activeId, taskState)
        session = TaskSessionState(
            tasks = listOf(activeTask),
            activeTaskId = activeTask.id
        )
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
            activeTaskId = DEFAULT_ACTIVE_TASK_ID
        )

    companion object {
        private const val DEFAULT_ACTIVE_TASK_ID = "task-1"
    }
}
