package llm.core.tokenizer

import llm.core.model.ChatMessage

interface TokenCounter {
    /**
     * Подсчитывает количество токенов в произвольном тексте.
     *
     * @param text текст для подсчёта
     * @return количество токенов
     */
    fun countText(text: String): Int

    /**
     * Подсчитывает суммарное количество токенов для списка сообщений.
     *
     * @param messages сообщения, для которых нужно посчитать токены
     * @return суммарное количество токенов
     */
    fun countMessages(messages: List<ChatMessage>): Int =
        messages.sumOf(::countMessage)

    /**
     * Подсчитывает количество токенов в одном сообщении с учётом его роли и содержимого.
     *
     * @param message сообщение, для которого нужно посчитать токены
     * @return количество токенов в сообщении
     */
    fun countMessage(message: ChatMessage): Int =
        countText("${message.role.apiValue}\n${message.content}")
}

