package app.output

import kotlinx.serialization.Serializable

/**
 * Одна запись debug/smoke trace, независимая от конкретного UI.
 */
@Serializable
data class DebugTraceRecord(
    val kind: String,
    val title: String,
    val lines: List<String>
)
