package agent.memory.strategy.summary

import llm.core.model.ChatMessage
import llm.core.model.ChatRole

class SimpleConversationSummarizer : ConversationSummarizer {
    override fun summarize(messages: List<ChatMessage>): String =
        messages.joinToString(separator = "\n") { message ->
            "${roleLabel(message.role)}: ${message.content}"
        }

    private fun roleLabel(role: ChatRole): String =
        when (role) {
            ChatRole.SYSTEM -> "Система"
            ChatRole.USER -> "Пользователь"
            ChatRole.ASSISTANT -> "Ассистент"
        }
}


