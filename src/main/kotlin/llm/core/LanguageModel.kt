package llm.core

import llm.core.model.ChatMessage
import llm.core.model.LanguageModelInfo
import llm.core.model.LanguageModelResponse
import llm.core.tokenizer.TokenCounter

interface LanguageModel {
    val info: LanguageModelInfo
    val tokenCounter: TokenCounter?

    /**
     * Выполняет запрос к языковой модели по переданному набору сообщений.
     *
     * @param messages сообщения, формирующие контекст запроса
     * @return ответ модели, включая текст и доступную статистику использования токенов
     */
    fun complete(messages: List<ChatMessage>): LanguageModelResponse
}
