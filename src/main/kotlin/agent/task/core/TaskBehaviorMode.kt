package agent.task.core

/**
 * Описывает режим ответа агента, который следует из текущего task state.
 */
enum class TaskBehaviorMode {
    EXECUTION,
    PLANNING,
    VALIDATION,
    COMPLETION
}
