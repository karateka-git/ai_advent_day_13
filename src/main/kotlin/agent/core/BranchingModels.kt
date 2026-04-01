package agent.core

/**
 * Краткие сведения о ветке диалога.
 *
 * @property name имя ветки.
 * @property sourceCheckpointName имя checkpoint, из которого была создана ветка, если он был.
 * @property isActive признак активной ветки.
 */
data class BranchInfo(
    val name: String,
    val sourceCheckpointName: String? = null,
    val isActive: Boolean = false
)

/**
 * Сведения о созданном checkpoint.
 *
 * @property name имя checkpoint.
 * @property sourceBranchName имя ветки, из которой был создан checkpoint.
 */
data class BranchCheckpointInfo(
    val name: String,
    val sourceBranchName: String
)

/**
 * Текущее состояние ветвления диалога.
 *
 * @property activeBranchName имя активной ветки.
 * @property latestCheckpointName имя последнего checkpoint, если он есть.
 * @property branches список доступных веток.
 */
data class BranchingStatus(
    val activeBranchName: String,
    val latestCheckpointName: String? = null,
    val branches: List<BranchInfo>
)
