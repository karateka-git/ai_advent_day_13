package agent.task.core

import agent.task.model.ExpectedAction
import agent.task.model.TaskStage
import agent.task.model.TaskState
import agent.task.model.TaskStatus

/**
 * In-memory реализация [TaskManager] для первого этапа task subsystem.
 *
 * На этом этапе менеджер управляет только одной текущей задачей без multitask и без
 * жёсткой валидации переходов между stage.
 */
class DefaultTaskManager(
    initialTask: TaskState? = null
) : TaskManager {
    private var current: TaskState? = initialTask

    override fun currentTask(): TaskState? = current

    override fun startTask(title: String): TaskState {
        require(title.isNotBlank()) { "Название задачи не должно быть пустым." }

        return TaskState(
            title = title.trim()
        ).also { createdTask ->
            current = createdTask
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
    }

    /**
     * Применяет изменение к текущей задаче и сохраняет обновлённое состояние.
     *
     * @param transform функция обновления состояния задачи.
     * @return обновлённая задача.
     */
    private fun updateCurrentTask(transform: (TaskState) -> TaskState): TaskState {
        val existingTask = current ?: error("Текущая задача ещё не создана.")
        return transform(existingTask).also { updatedTask ->
            current = updatedTask
        }
    }
}
