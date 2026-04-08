package agent.task.model

import kotlinx.serialization.Serializable

/**
 * Формализованное состояние одной текущей задачи.
 *
 * @property title короткое название рабочего трека.
 * @property stage текущий этап задачи.
 * @property currentStep текущий зафиксированный шаг внутри задачи.
 * @property expectedAction ближайшее ожидаемое действие для продвижения задачи.
 * @property status жизненный статус задачи.
 */
@Serializable
data class TaskState(
    val title: String,
    val stage: TaskStage = TaskStage.PLANNING,
    val currentStep: String? = null,
    val expectedAction: ExpectedAction = ExpectedAction.NONE,
    val status: TaskStatus = TaskStatus.ACTIVE
)
