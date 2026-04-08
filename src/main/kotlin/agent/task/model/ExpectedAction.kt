package agent.task.model

/**
 * Ближайшее ожидаемое действие, нужное для продвижения задачи.
 *
 * Это поле не дублирует stage, а задаёт следующий необходимый шаг.
 */
enum class ExpectedAction {
    USER_INPUT,
    AGENT_EXECUTION,
    USER_CONFIRMATION,
    NONE
}
