package agent.task.core

import agent.task.model.ExpectedAction
import agent.task.model.TaskState

/**
 * Решение orchestration-слоя о том, как task state должен повлиять на следующий ответ агента.
 */
sealed interface TaskGuardDecision {
    /**
     * Специальных task-aware ограничений нет.
     */
    data object None : TaskGuardDecision

    /**
     * Обычный workflow нужно заблокировать и вернуть детерминированный ответ без продолжения задачи.
     *
     * @property reason причина блокировки.
     * @property task задача, из-за которой сработала блокировка.
     * @property message понятное объяснение для пользователя.
     */
    data class Block(
        val reason: TaskBlockReason,
        val task: TaskState,
        val message: String
    ) : TaskGuardDecision

    /**
     * Модельный запрос допустим, но ответ нужно направить в определённый task-aware режим.
     *
     * @property task текущая задача.
     * @property mode режим ответа.
     * @property expectedAction ближайшее ожидаемое действие, если оно явно важно для поведения.
     * @property guidance краткая поведенческая инструкция для агента.
     */
    data class Guide(
        val task: TaskState,
        val mode: TaskBehaviorMode,
        val expectedAction: ExpectedAction?,
        val guidance: String
    ) : TaskGuardDecision
}
