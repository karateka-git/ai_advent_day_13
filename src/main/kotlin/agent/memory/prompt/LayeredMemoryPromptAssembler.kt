package agent.memory.prompt

import agent.memory.model.LongTermMemory
import agent.memory.model.MemoryNote
import agent.memory.model.WorkingMemory
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

/**
 * Собирает финальный prompt из системного prompt и layered memory.
 */
class LayeredMemoryPromptAssembler {
    /**
     * Формирует итоговый prompt в порядке: system prompt, long-term memory, working memory,
     * затем short-term контекст выбранной стратегии.
     *
     * @param systemPrompt базовый системный prompt агента.
     * @param longTermMemory долговременная память, которую нужно включить в prompt.
     * @param workingMemory рабочая память текущей задачи.
     * @param shortTermContext short-term контекст, подготовленный активной стратегией.
     * @return итоговый список сообщений для отправки в LLM.
     */
    fun assemble(
        systemPrompt: String,
        longTermMemory: LongTermMemory,
        workingMemory: WorkingMemory,
        shortTermContext: List<ChatMessage>
    ): List<ChatMessage> {
        val layeredSystemPrompt = buildLayeredSystemPrompt(
            systemPrompt = systemPrompt,
            longTermMemory = longTermMemory,
            workingMemory = workingMemory
        )
        val firstSystemIndex = shortTermContext.indexOfFirst { it.role == ChatRole.SYSTEM }

        return if (firstSystemIndex >= 0) {
            shortTermContext.mapIndexed { index, message ->
                if (index == firstSystemIndex) {
                    message.copy(content = layeredSystemPrompt)
                } else {
                    message
                }
            }
        } else {
            listOf(ChatMessage(role = ChatRole.SYSTEM, content = layeredSystemPrompt)) + shortTermContext
        }
    }

    /**
     * Собирает итоговое содержимое системного prompt с блоками long-term и working memory.
     *
     * @param systemPrompt базовый системный prompt.
     * @param longTermMemory долговременная память.
     * @param workingMemory рабочая память.
     * @return текст первого системного сообщения assembled prompt.
     */
    private fun buildLayeredSystemPrompt(
        systemPrompt: String,
        longTermMemory: LongTermMemory,
        workingMemory: WorkingMemory
    ): String =
        buildString {
            append(systemPrompt.trim())

            formatNotesSection(
                title = "Long-term memory",
                notes = longTermMemory.notes
            )?.let {
                append("\n\n")
                append(it)
            }

            formatNotesSection(
                title = "Working memory",
                notes = workingMemory.notes
            )?.let {
                append("\n\n")
                append(it)
            }
        }

    /**
     * Форматирует отдельную секцию памяти для системного prompt.
     *
     * @param title заголовок секции.
     * @param notes заметки выбранного слоя памяти.
     * @return текст секции или `null`, если слой пуст.
     */
    private fun formatNotesSection(title: String, notes: List<MemoryNote>): String? {
        if (notes.isEmpty()) {
            return null
        }

        return buildString {
            appendLine(title)
            notes.forEach { note ->
                appendLine("- ${note.category}: ${note.content}")
            }
        }.trimEnd()
    }
}
