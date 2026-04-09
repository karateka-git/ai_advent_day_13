package agent.task.core

import agent.task.model.ExpectedAction
import agent.task.model.TaskSessionState
import agent.task.model.TaskStage
import agent.task.model.TaskState
import agent.task.prompt.TaskPromptContext

/**
 * Управляет task session state и совместимым single-task API активной задачи.
 */
interface TaskManager {
    /**
     * Возвращает полное состояние task session.
     */
    fun sessionState(): TaskSessionState

    /**
     * Возвращает текущую активную задачу или `null`, если активная задача ещё не создана.
     */
    fun currentTask(): TaskState?

    /**
     * Возвращает task-derived данные для итогового system prompt.
     *
     * @return prompt context текущей активной задачи без прямой модификации `system message`.
     */
    fun promptContext(): TaskPromptContext

    /**
     * Создаёт новую текущую активную задачу.
     *
     * @param title название рабочего трека.
     * @return созданное состояние активной задачи.
     */
    fun startTask(title: String): TaskState

    /**
     * Обновляет stage текущей активной задачи.
     */
    fun updateStage(stage: TaskStage): TaskState

    /**
     * Обновляет текущий шаг активной задачи.
     */
    fun updateStep(step: String): TaskState

    /**
     * Обновляет ожидаемое действие активной задачи.
     */
    fun updateExpectedAction(action: ExpectedAction): TaskState

    /**
     * Ставит активную задачу на паузу.
     */
    fun pauseTask(): TaskState

    /**
     * Возобновляет активную задачу.
     */
    fun resumeTask(): TaskState

    /**
     * Завершает активную задачу.
     */
    fun completeTask(): TaskState

    /**
     * Полностью очищает активную задачу и task session.
     */
    fun clearTask()
}
