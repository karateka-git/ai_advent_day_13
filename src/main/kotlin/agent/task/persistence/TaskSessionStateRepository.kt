package agent.task.persistence

import agent.task.model.TaskSessionState

/**
 * Репозиторий полного conversation-scoped task session state.
 */
interface TaskSessionStateRepository {
    /**
     * Загружает сохранённое состояние task session.
     *
     * @return сохранённая task session или `null`, если она ещё не была создана.
     */
    fun load(): TaskSessionState?

    /**
     * Сохраняет полное состояние task session.
     *
     * @param state актуальное состояние task session.
     */
    fun save(state: TaskSessionState)

    /**
     * Очищает сохранённое состояние task session.
     */
    fun clear()
}
