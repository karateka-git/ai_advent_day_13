package agent.memory.strategy.facts

import llm.core.model.ChatMessage

/**
 * Извлекает устойчивые facts из нового батча сообщений с учётом уже известных данных.
 */
fun interface ConversationFactsExtractor {
    fun extract(
        existingFacts: Map<String, String>,
        newMessagesBatch: List<ChatMessage>
    ): Map<String, String>
}


