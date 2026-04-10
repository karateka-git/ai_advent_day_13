package agent.task.model

import kotlinx.serialization.Serializable

/**
 * Отдельная задача внутри task session state.
 *
 * @property id стабильный идентификатор задачи внутри текущей task session.
 * @property title короткое название рабочего трека.
 * @property stage текущий этап задачи.
 * @property currentStep текущий зафиксированный шаг внутри задачи.
 * @property expectedAction ближайшее ожидаемое действие для продвижения задачи.
 * @property status жизненный статус задачи.
 */
@Serializable
data class TaskItem(
    val id: String,
    val title: String,
    val stage: TaskStage = TaskStage.PLANNING,
    val currentStep: String? = null,
    val expectedAction: ExpectedAction = ExpectedAction.NONE,
    val status: TaskStatus = TaskStatus.ACTIVE
) {
    /**
     * Возвращает совместимое single-task представление задачи.
     */
    fun toTaskState(): TaskState = TaskState(
        title = title,
        stage = stage,
        currentStep = currentStep,
        expectedAction = expectedAction,
        status = status
    )

    companion object {
        /**
         * Создаёт [TaskItem] из совместимого single-task представления [TaskState].
         */
        fun fromTaskState(id: String, taskState: TaskState): TaskItem = TaskItem(
            id = id,
            title = taskState.title,
            stage = taskState.stage,
            currentStep = taskState.currentStep,
            expectedAction = taskState.expectedAction,
            status = taskState.status
        )
    }
}
