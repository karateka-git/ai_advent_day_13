package agent.task.core

import agent.task.model.ExpectedAction
import agent.task.model.TaskStage
import agent.task.model.TaskState
import agent.task.model.TaskStatus
import agent.task.model.TaskStages
import agent.task.persistence.TaskStateRepository
import agent.task.prompt.TaskPromptContext

/**
 * In-memory реализация [TaskManager] для первого этапа task subsystem.
 *
 * На этом этапе менеджер управляет только одной текущей задачей без multitask и без
 * жёсткой валидации переходов между stage.
 */
class DefaultTaskManager(
    initialTask: TaskState? = null,
    private val repository: TaskStateRepository? = null
) : TaskManager {
    private var current: TaskState? = initialTask ?: repository?.load()

    override fun currentTask(): TaskState? = current

    override fun promptContext(): TaskPromptContext =
        TaskPromptContext(
            systemPromptContribution = current?.let(::buildPromptContribution)
        )

    override fun startTask(title: String): TaskState {
        require(title.isNotBlank()) { "Название задачи не должно быть пустым." }

        return TaskState(
            title = title.trim()
        ).also { createdTask ->
            setCurrent(createdTask)
        }
    }

    override fun updateStage(stage: TaskStage): TaskState =
        updateCurrentTask { task ->
            task.copy(stage = stage)
        }

    override fun updateStep(step: String): TaskState {
        require(step.isNotBlank()) { "Текущий шаг не должен быть пустым." }

        return updateCurrentTask { task ->
            task.copy(currentStep = step.trim())
        }
    }

    override fun updateExpectedAction(action: ExpectedAction): TaskState =
        updateCurrentTask { task ->
            task.copy(expectedAction = action)
        }

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
        updateCurrentTask { task ->
            task.copy(status = TaskStatus.DONE)
        }

    override fun clearTask() {
        current = null
        repository?.clear()
    }

    /**
     * Применяет изменение к текущей задаче и сохраняет обновлённое состояние.
     *
     * @param transform функция обновления состояния задачи.
     * @return обновлённая задача.
     */
    private fun updateCurrentTask(transform: (TaskState) -> TaskState): TaskState {
        val existingTask = current ?: error("Текущая задача ещё не создана.")
        return transform(existingTask).also(::setCurrent)
    }

    /**
     * Обновляет текущую задачу в памяти и при наличии repository сохраняет её.
     *
     * @param task новое состояние задачи.
     */
    private fun setCurrent(task: TaskState) {
        current = task
        repository?.save(task)
    }

    /**
     * Формирует task-derived contribution для итогового system prompt.
     *
     * На этапе 1 task subsystem сама определяет, какой компактный контекст о текущей задаче нужен
     * модели, но не модифицирует `system message` напрямую.
     */
    private fun buildPromptContribution(taskState: TaskState): String {
        val stageDefinition = TaskStages.definitionFor(taskState.stage)
        return buildString {
            appendLine("Task state")
            appendLine("- Title: ${taskState.title}")
            appendLine("- Stage: ${stageDefinition.label}")
            appendLine("- Status: ${statusLabel(taskState.status)}")
            appendLine("- Expected action: ${expectedActionLabel(taskState.expectedAction)}")
            appendLine("- Stage details: ${stageDefinition.description}")
            append("- Current step: ${taskState.currentStep ?: "(not specified)"}")
        }
    }

    private fun statusLabel(status: TaskStatus): String =
        when (status) {
            TaskStatus.ACTIVE -> "active"
            TaskStatus.PAUSED -> "paused"
            TaskStatus.DONE -> "done"
        }

    private fun expectedActionLabel(action: ExpectedAction): String =
        when (action) {
            ExpectedAction.USER_INPUT -> "user_input"
            ExpectedAction.AGENT_EXECUTION -> "agent_execution"
            ExpectedAction.USER_CONFIRMATION -> "user_confirmation"
            ExpectedAction.NONE -> "none"
        }
}
