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
 * @property category доменная категория заметки, например `goal` или `communication_style`.
 * @property content нормализованное текстовое содержимое заметки.
 */
data class MemoryNote(
    val category: String,
    val content: String
)

/**
 * Краткосрочная память: текущий диалог и derived state выбранной стратегии.
 *
 * @property messages накопленный short-term диалог.
 * @property strategyState derived state активной short-term стратегии.
 */
data class ShortTermMemory(
    val messages: List<ChatMessage> = emptyList(),
    val strategyState: StrategyState? = null
)

/**
 * Рабочая память: данные текущей задачи.
 *
 * @property notes список заметок, полезных для выполнения текущей задачи.
 */
data class WorkingMemory(
    val notes: List<MemoryNote> = emptyList()
)

/**
 * Долговременная память: устойчивые предпочтения, договорённости и знания.
 *
 * @property notes список устойчивых заметок, полезных в будущих диалогах.
 */
data class LongTermMemory(
    val notes: List<MemoryNote> = emptyList()
)

/**
 * Полный in-memory снимок памяти ассистента с явным разделением по слоям.
 *
 * @property shortTerm краткосрочная память и derived state active strategy.
 * @property working рабочая память текущей задачи.
 * @property longTerm долговременная память пользователя и проекта.
 */
data class MemoryState(
    val shortTerm: ShortTermMemory = ShortTermMemory(),
    val working: WorkingMemory = WorkingMemory(),
    val longTerm: LongTermMemory = LongTermMemory()
)
