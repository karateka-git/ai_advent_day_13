package agent.task.model

import kotlinx.serialization.Serializable

/**
 * Контейнер task subsystem для нескольких задач внутри одной conversation-scoped сессии.
 *
 * @property tasks список всех известных задач.
 * @property activeTaskId идентификатор активной задачи или `null`, если активной задачи нет.
 */
@Serializable
data class TaskSessionState(
    val tasks: List<TaskItem> = emptyList(),
    val activeTaskId: String? = null
) {
    init {
        require(activeTaskId == null || tasks.any { it.id == activeTaskId }) {
            "activeTaskId должен указывать на существующую задачу или быть null."
        }
    }

    /**
     * Возвращает активную задачу или `null`, если она не определена.
     */
    fun activeTask(): TaskItem? = tasks.firstOrNull { it.id == activeTaskId }
}
