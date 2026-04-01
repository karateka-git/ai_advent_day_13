package agent.storage.mapper

import agent.storage.model.StoredMessage
import llm.core.model.ChatMessage

interface ConversationMapper {
    /**
     * Преобразует рабочую модель сообщения в формат хранения.
     *
     * @param message сообщение диалога в рабочем формате
     * @return сообщение в формате хранения
     */
    fun toStoredMessage(message: ChatMessage): StoredMessage

    /**
     * Преобразует сообщение из формата хранения в рабочую модель диалога.
     *
     * @param message сообщение в формате хранения
     * @return сообщение в рабочем формате
     */
    fun fromStoredMessage(message: StoredMessage): ChatMessage
}

