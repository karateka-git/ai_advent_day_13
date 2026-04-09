package agent.task.core

/**
 * Причина, по которой orchestration-слой должен заблокировать обычное продолжение workflow.
 */
enum class TaskBlockReason {
    PAUSED
}
