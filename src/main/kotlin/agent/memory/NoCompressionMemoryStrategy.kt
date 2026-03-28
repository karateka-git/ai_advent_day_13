package agent.memory

import llm.core.model.ChatMessage

class NoCompressionMemoryStrategy : MemoryStrategy {
    override fun messagesForModel(messages: List<ChatMessage>): List<ChatMessage> =
        messages.toList()
}
