package agent.task.core

import agent.task.model.ExpectedAction
import agent.task.model.TaskItem
import agent.task.model.TaskSessionState
import agent.task.model.TaskStage
import agent.task.model.TaskState
import agent.task.prompt.TaskPromptContext

/**
 * Управляет task session state и предоставляет доступ к активной задаче и task-oriented операциям.
 */
interface TaskManager {
    /**
     * Возвращает полное состояние task session.
     */
    fun sessionState(): TaskSessionState

    /**
     * Возвращает все задачи, известные текущей session.
     */
    fun listTasks(): List<TaskItem>

    /**
     * Возвращает текущую активную задачу session или `null`, если активная задача сейчас отсутствует.
     */
    fun activeTask(): TaskItem?

    /**
     * Возвращает текущую сфокусированную задачу в совместимом single-task виде.
     *
     * В первую очередь это active task. Если активной задачи нет, метод может вернуть
     * последний сохранённый рабочий трек для совместимого single-task view.
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
     * Возобновляет активную задачу или последнюю paused-задачу через совместимый single-task flow.
     */
    fun resumeTask(): TaskState

    /**
     * Переключает active task на указанную задачу.
     */
    fun switchTask(taskId: String): TaskState

    /**
     * Возобновляет задачу по `taskId`.
     */
    fun resumeTask(taskId: String): TaskState

    /**
     * Завершает активную задачу.
     */
    fun completeTask(): TaskState

    /**
     * Завершает задачу по `taskId`.
     */
    fun completeTask(taskId: String): TaskState

    /**
     * Полностью очищает активную задачу и task session.
     */
    fun clearTask()
}
