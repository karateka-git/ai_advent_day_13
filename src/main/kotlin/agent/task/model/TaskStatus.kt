package agent.task.model

import kotlinx.serialization.Serializable

/**
 * Жизненный статус задачи.
 */
@Serializable
enum class TaskStatus {
    ACTIVE,
    PAUSED,
    DONE
}
