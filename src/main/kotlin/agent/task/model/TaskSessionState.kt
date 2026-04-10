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
        require(tasks.map { it.id }.distinct().size == tasks.size) {
            "task ids должны быть уникальными."
        }
        require(activeTaskId == null || tasks.any { it.id == activeTaskId }) {
            "activeTaskId должен указывать на существующую задачу или быть null."
        }
        require(activeTaskId == null || task(activeTaskId)?.status == TaskStatus.ACTIVE) {
            "activeTaskId должен указывать на активную задачу."
        }
        require(tasks.count { it.status == TaskStatus.ACTIVE } <= 1) {
            "В session может быть только одна активная задача."
        }
        require(tasks.none { it.status == TaskStatus.ACTIVE && it.id != activeTaskId }) {
            "Активная задача должна совпадать с activeTaskId."
        }
    }

    /**
     * Возвращает активную задачу или `null`, если она не определена.
     */
    fun activeTask(): TaskItem? = tasks.firstOrNull { it.id == activeTaskId }

    /**
     * Возвращает задачу по `taskId` или `null`, если она не найдена.
     */
    fun task(taskId: String): TaskItem? = tasks.firstOrNull { it.id == taskId }
}
