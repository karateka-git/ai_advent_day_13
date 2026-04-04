package agent.memory.layer

import agent.memory.model.MemoryNote

/**
 * Определяет, как объединять существующие и новые заметки внутри одного слоя памяти.
 */
interface MemoryNoteMergePolicy {
    /**
     * Объединяет существующие и новые заметки по правилам конкретной категории.
     *
     * @param existing текущие заметки слоя.
     * @param additions новые заметки, извлечённые из последнего сообщения.
     * @return итоговый набор заметок после объединения.
     */
    fun merge(existing: List<MemoryNote>, additions: List<MemoryNote>): List<MemoryNote>
}

/**
 * Реализация на основе правил: для категорий с единственным значением оставляет только последнее,
 * а для категорий с несколькими значениями добавляет только уникальные заметки.
 */
class RuleBasedMemoryNoteMergePolicy : MemoryNoteMergePolicy {
    /**
     * Объединяет заметки по правилам категории и предотвращает бесконтрольное накопление противоречий.
     *
     * @param existing текущие заметки слоя.
     * @param additions новые заметки, полученные от распределителя памяти.
     * @return итоговый набор заметок после объединения.
     */
    override fun merge(existing: List<MemoryNote>, additions: List<MemoryNote>): List<MemoryNote> =
        additions.fold(existing) { current, addition ->
            when (mergeModeFor(addition.category)) {
                MergeMode.REPLACE_CATEGORY ->
                    current
                        .filterNot { it.category == addition.category }
                        .plus(addition)

                MergeMode.APPEND_DISTINCT ->
                    if (current.any { it.category == addition.category && it.content.equals(addition.content, ignoreCase = true) }) {
                        current
                    } else {
                        current + addition
                    }
            }
        }

    private fun mergeModeFor(category: String): MergeMode =
        if (category in singleValueCategories) {
            MergeMode.REPLACE_CATEGORY
        } else {
            MergeMode.APPEND_DISTINCT
        }

    private enum class MergeMode {
        REPLACE_CATEGORY,
        APPEND_DISTINCT
    }

    private companion object {
        val singleValueCategories = setOf(
            "goal",
            "deadline",
            "budget",
            "communication_style"
        )
    }
}
