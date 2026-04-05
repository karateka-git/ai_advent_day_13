package agent.memory.model

/**
 * Изменение уже сохранённой durable-заметки.
 */
sealed interface ManagedMemoryNoteEdit {
    /**
     * Заменяет текст заметки.
     */
    data class UpdateText(
        val content: String
    ) : ManagedMemoryNoteEdit

    /**
     * Меняет категорию заметки внутри того же слоя памяти.
     */
    data class UpdateCategory(
        val category: String
    ) : ManagedMemoryNoteEdit
}

/**
 * Результат операции над сохранённой durable-заметкой.
 *
 * @property note заметка после изменения; для удаления содержит удалённую заметку.
 * @property state новое состояние памяти после операции.
 */
data class ManagedMemoryNoteResult(
    val note: MemoryNote,
    val state: MemoryState
)
