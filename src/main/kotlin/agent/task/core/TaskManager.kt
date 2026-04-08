package agent.task.core

import agent.task.model.ExpectedAction
import agent.task.model.TaskStage
import agent.task.model.TaskState

/**
 * Управляет одной текущей задачей в рамках первого этапа task subsystem.
 */
interface TaskManager {
    /**
     * Возвращает текущую задачу или `null`, если она ещё не создана.
     */
    fun currentTask(): TaskState?

    /**
     * Создаёт новую текущую задачу.
     *
     * @param title название рабочего трека.
     * @return созданное состояние задачи.
     */
    fun startTask(title: String): TaskState

    /**
     * Обновляет stage текущей задачи.
     *
     * @param stage новый этап задачи.
     * @return обновлённое состояние задачи.
     */
    fun updateStage(stage: TaskStage): TaskState

    /**
     * Обновляет текущий шаг задачи.
     *
     * @param step новый текущий шаг.
     * @return обновлённое состояние задачи.
     */
    fun updateStep(step: String): TaskState

    /**
     * Обновляет ожидаемое действие задачи.
     *
     * @param action новое ожидаемое действие.
     * @return обновлённое состояние задачи.
     */
    fun updateExpectedAction(action: ExpectedAction): TaskState

    /**
     * Ставит текущую задачу на паузу.
     *
     * @return обновлённое состояние задачи.
     */
    fun pauseTask(): TaskState

    /**
     * Возобновляет текущую задачу.
     *
     * @return обновлённое состояние задачи.
     */
    fun resumeTask(): TaskState

    /**
     * Завершает текущую задачу.
     *
     * @return обновлённое состояние задачи.
     */
    fun completeTask(): TaskState

    /**
     * Полностью очищает текущую задачу.
     */
    fun clearTask()
}
