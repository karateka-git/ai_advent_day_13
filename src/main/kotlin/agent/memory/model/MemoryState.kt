package agent.memory.model

import llm.core.model.ChatMessage

/**
 * Тип слоя памяти ассистента.
 */
enum class MemoryLayer {
    SHORT_TERM,
    WORKING,
    LONG_TERM
}

/**
 * Краткая единица явно сохранённой рабочей или долговременной памяти.
 *
 * @property id устойчивый идентификатор заметки внутри persisted state.
 * @property category доменная категория заметки, например `goal` или `communication_style`.
 * @property content нормализованное текстовое содержимое заметки.
 */
data class MemoryNote(
    val id: String,
    val category: String,
    val content: String
) {
    constructor(category: String, content: String) : this(
        id = "",
        category = category,
        content = content
    )
}

/**
 * Краткосрочная память: сырой журнал текущей сессии и его представление,
 * вычисленное активной short-term стратегией.
 */
data class ShortTermMemory(
    val rawMessages: List<ChatMessage> = emptyList(),
    val derivedMessages: List<ChatMessage> = emptyList(),
    val strategyState: StrategyState? = null
)

/**
 * Рабочая память: данные текущей задачи.
 */
data class WorkingMemory(
    val notes: List<MemoryNote> = emptyList()
)

/**
 * Долговременная память: устойчивые предпочтения, договорённости и знания.
 */
data class LongTermMemory(
    val notes: List<MemoryNote> = emptyList()
)

/**
 * Полный in-memory снимок памяти ассистента с явным разделением по слоям.
 *
 * @property nextNoteId следующий числовой идентификатор для новой durable-заметки.
 */
data class MemoryState(
    val shortTerm: ShortTermMemory = ShortTermMemory(),
    val working: WorkingMemory = WorkingMemory(),
    val longTerm: LongTermMemory = LongTermMemory(),
    val pending: PendingMemoryState = PendingMemoryState(),
    val nextNoteId: Long = 1
)
