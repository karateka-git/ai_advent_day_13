package agent.memory.layer

import agent.memory.model.LongTermMemory
import agent.memory.model.MemoryNote
import agent.memory.model.MemoryState
import agent.memory.model.WorkingMemory
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

/**
 * Явно решает, что из нового сообщения нужно сохранить в рабочую и долговременную память.
 */
interface MemoryLayerAllocator {
    /**
     * Распределяет новое сообщение по слоям памяти поверх текущего состояния.
     *
     * @param state текущее состояние многослойной памяти.
     * @param message новое сообщение, которое нужно проанализировать.
     * @return обновлённые рабочий и долговременный слои памяти.
     */
    fun allocate(state: MemoryState, message: ChatMessage): MemoryLayerAllocation
}

/**
 * Результат распределения данных по слоям памяти.
 *
 * @property workingMemory обновлённая рабочая память.
 * @property longTermMemory обновлённая долговременная память.
 */
data class MemoryLayerAllocation(
    val workingMemory: WorkingMemory = WorkingMemory(),
    val longTermMemory: LongTermMemory = LongTermMemory()
)

/**
 * Простая реализация распределителя памяти на основе правил.
 *
 * Извлекает отдельные смысловые фрагменты из сообщения и объединяет их с текущей памятью
 * по правилам выбранной политики объединения.
 */
class RuleBasedMemoryLayerAllocator(
    private val noteMergePolicy: MemoryNoteMergePolicy = RuleBasedMemoryNoteMergePolicy()
) : MemoryLayerAllocator {
    /**
     * Выделяет заметки рабочей и долговременной памяти по набору строковых маркеров.
     *
     * @param state текущее состояние памяти.
     * @param message новое сообщение пользователя или ассистента.
     * @return обновлённые рабочий и долговременный слои.
     */
    override fun allocate(state: MemoryState, message: ChatMessage): MemoryLayerAllocation {
        if (message.role == ChatRole.SYSTEM) {
            return MemoryLayerAllocation(
                workingMemory = state.working,
                longTermMemory = state.longTerm
            )
        }

        val segments = extractSegments(message.content)
        val workingNotes = buildList {
            maybeAdd("goal", segments, goalMarkers)
            maybeAdd("constraint", segments, constraintMarkers)
            maybeAdd("deadline", segments, deadlineMarkers)
            maybeAdd("budget", segments, budgetMarkers)
            maybeAdd("integration", segments, integrationMarkers)
            maybeAdd("decision", segments, decisionMarkers)
            maybeAdd("open_question", segments, openQuestionMarkers)
        }

        val longTermNotes = buildList {
            maybeAdd("communication_style", segments, communicationStyleMarkers)
            maybeAdd("persistent_preference", segments, preferenceMarkers)
            maybeAdd("architectural_agreement", segments, architectureMarkers)
            maybeAdd("reusable_knowledge", segments, reusableKnowledgeMarkers)
        }

        return MemoryLayerAllocation(
            workingMemory = WorkingMemory(
                notes = noteMergePolicy.merge(state.working.notes, workingNotes)
            ),
            longTermMemory = LongTermMemory(
                notes = noteMergePolicy.merge(state.longTerm.notes, longTermNotes)
            )
        )
    }

    /**
     * Добавляет заметки по всем сегментам сообщения, которые совпали с маркерами выбранной категории.
     *
     * @param category категория заметки.
     * @param segments смысловые сегменты исходного сообщения.
     * @param markers список маркеров категории.
     */
    private fun MutableList<MemoryNote>.maybeAdd(
        category: String,
        segments: List<String>,
        markers: List<String>
    ) {
        segments
            .filter { segment ->
                val normalized = segment.lowercase()
                markers.any { marker -> normalized.contains(marker) }
            }
            .forEach { segment ->
                add(MemoryNote(category = category, content = segment))
            }
    }

    /**
     * Делит сообщение на смысловые сегменты, чтобы рабочая и долговременная память
     * не хранили исходный текст целиком.
     *
     * @param content исходный текст сообщения.
     * @return очищенные непустые сегменты сообщения.
     */
    private fun extractSegments(content: String): List<String> =
        content
            .split(segmentsDelimiter)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .ifEmpty { listOf(content.trim()) }

    private companion object {
        val segmentsDelimiter = Regex("[.!?\\n]+")

        val goalMarkers = listOf(
            "цель",
            "задача",
            "mvp",
            "нужно сделать",
            "нужно реализовать",
            "нужно собрать",
            "нужно внедрить"
        )
        val constraintMarkers = listOf(
            "ограничени",
            "только",
            "без",
            "нельзя",
            "не делать",
            "должен"
        )
        val deadlineMarkers = listOf(
            "срок",
            "дедлайн",
            "недел",
            "дня",
            "дней",
            "до "
        )
        val budgetMarkers = listOf(
            "бюджет",
            "стоимост",
            "руб",
            "тысяч"
        )
        val integrationMarkers = listOf(
            "интеграц",
            "google sheets",
            "telegram api",
            "crm",
            "api"
        )
        val decisionMarkers = listOf(
            "решили",
            "решение",
            "выбираем",
            "будем",
            "оставляем",
            "убираем",
            "добавляем"
        )
        val openQuestionMarkers = listOf(
            "открытый вопрос",
            "открытые вопросы",
            "нужно решить",
            "непонятно",
            "вопрос",
            "как"
        )
        val communicationStyleMarkers = listOf(
            "отвечай",
            "пиши",
            "коротко",
            "подробно",
            "на русском",
            "стиль общения"
        )
        val preferenceMarkers = listOf(
            "предпочита",
            "люблю",
            "всегда",
            "не используй",
            "хочу"
        )
        val architectureMarkers = listOf(
            "архитектур",
            "business logic",
            "ui должен",
            "не завязывать",
            "договоренн",
            "в проекте используем"
        )
        val reusableKnowledgeMarkers = listOf(
            "проект",
            "ассистент",
            "пользователь",
            "полезно повторно",
            "устойчив"
        )
    }
}
