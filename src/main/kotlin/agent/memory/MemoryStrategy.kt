package agent.memory

import llm.core.model.ChatMessage

interface MemoryStrategy {
    /**
     * Формирует представление диалога, которое должно быть отправлено в языковую модель.
     *
     * @param messages сообщения, хранящиеся в памяти менеджера
     * @return сообщения, которые нужно использовать как эффективный контекст запроса
     */
    fun messagesForModel(messages: List<ChatMessage>): List<ChatMessage>
}
