package agent.task.persistence

import agent.task.model.TaskState

/**
 * Репозиторий текущей conversation-scoped задачи.
 */
interface TaskStateRepository {
    /**
     * Загружает текущее состояние задачи.
     *
     * @return сохранённая задача или `null`, если задача ещё не была создана.
     */
    fun load(): TaskState?

    /**
     * Сохраняет текущее состояние задачи.
     *
     * @param state актуальное состояние задачи.
     */
    fun save(state: TaskState)

    /**
     * Очищает сохранённое состояние задачи.
     */
    fun clear()
}
