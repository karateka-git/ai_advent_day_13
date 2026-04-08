package agent.task.model

import kotlinx.serialization.Serializable

/**
 * Типизированный этап задачи в рамках B-lite модели task subsystem.
 *
 * Стартовый набор stage не считается окончательным и может быть расширен в будущем.
 */
@Serializable
enum class TaskStage {
    PLANNING,
    EXECUTION,
    VALIDATION,
    COMPLETION
}

/**
 * Metadata-описание stage для UI, prompt assembly и orchestration-логики.
 *
 * @property stage типизированное значение stage.
 * @property label короткое пользовательское имя stage.
 * @property description пояснение смысла stage.
 */
data class TaskStageDefinition(
    val stage: TaskStage,
    val label: String,
    val description: String
)

/**
 * Единый каталог стартовых stage definitions для task subsystem.
 */
object TaskStages {
    private val definitions = listOf(
        TaskStageDefinition(
            stage = TaskStage.PLANNING,
            label = "Планирование",
            description = "Уточнение задачи, подхода и ближайших шагов"
        ),
        TaskStageDefinition(
            stage = TaskStage.EXECUTION,
            label = "Выполнение",
            description = "Непосредственная работа над задачей"
        ),
        TaskStageDefinition(
            stage = TaskStage.VALIDATION,
            label = "Проверка",
            description = "Проверка результата и поиск недочётов"
        ),
        TaskStageDefinition(
            stage = TaskStage.COMPLETION,
            label = "Завершение",
            description = "Оформление итога и закрытие задачи"
        )
    )

    /**
     * Возвращает все стартовые definitions в стабильном порядке отображения.
     */
    fun all(): List<TaskStageDefinition> = definitions

    /**
     * Возвращает definition для указанного stage.
     *
     * @param stage этап, для которого нужна metadata.
     * @return metadata выбранного stage.
     */
    fun definitionFor(stage: TaskStage): TaskStageDefinition =
        definitions.first { it.stage == stage }
}
