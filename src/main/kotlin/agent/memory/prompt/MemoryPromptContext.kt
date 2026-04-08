package agent.memory.prompt

import llm.core.model.ChatMessage

/**
 * Представляет memory-вклад в финальный prompt без прямого редактирования system message.
 *
 * @property messages история сообщений для модели после short-term стратегии.
 * @property systemPromptContribution текстовый блок, который memory subsystem предлагает добавить в system prompt.
 */
data class MemoryPromptContext(
    val messages: List<ChatMessage>,
    val systemPromptContribution: String?
)
