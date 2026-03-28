package agent.memory

import agent.core.AgentTokenStats
import agent.storage.JsonConversationStore
import agent.storage.mapper.ChatMessageConversationMapper
import java.nio.file.Path
import llm.core.LanguageModel
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

class DefaultMemoryManager(
    private val languageModel: LanguageModel,
    private val systemPrompt: String,
    private val conversationStore: JsonConversationStore = JsonConversationStore.forLanguageModel(languageModel),
    private val memoryStrategy: MemoryStrategy = NoCompressionMemoryStrategy()
) : MemoryManager {
    private val conversationMapper = ChatMessageConversationMapper()
    private val conversation = loadConversation().toMutableList()

    override fun currentConversation(): List<ChatMessage> = conversation.toList()

    override fun previewTokenStats(userPrompt: String): AgentTokenStats {
        val effectiveConversation = effectiveConversation()
        val historyTokens = languageModel.tokenCounter?.countMessages(effectiveConversation)
        val userPromptTokens = languageModel.tokenCounter?.countText(userPrompt)
        val promptTokensLocal = languageModel.tokenCounter?.countMessages(
            memoryStrategy.messagesForModel(
                conversation + ChatMessage(role = ChatRole.USER, content = userPrompt)
            )
        )

        return AgentTokenStats(
            historyTokens = historyTokens,
            promptTokensLocal = promptTokensLocal,
            userPromptTokens = userPromptTokens
        )
    }

    override fun appendUserMessage(userPrompt: String): List<ChatMessage> {
        conversation += ChatMessage(role = ChatRole.USER, content = userPrompt)
        saveConversation()
        return effectiveConversation()
    }

    override fun appendAssistantMessage(content: String) {
        conversation += ChatMessage(role = ChatRole.ASSISTANT, content = content)
        saveConversation()
    }

    override fun clear() {
        conversation.clear()
        conversation += createSystemMessage()
        saveConversation()
    }

    override fun replaceContextFromFile(sourcePath: Path) {
        val importedMessages = JsonConversationStore(sourcePath).load()
            .map(conversationMapper::fromStoredMessage)

        require(importedMessages.isNotEmpty()) {
            "Файл истории $sourcePath пустой или не содержит сообщений."
        }

        conversation.clear()
        conversation += importedMessages
        saveConversation()
    }

    private fun loadConversation(): List<ChatMessage> {
        val savedConversation = conversationStore.load()
            .map(conversationMapper::fromStoredMessage)
        if (savedConversation.isNotEmpty()) {
            return savedConversation
        }

        val initialConversation = listOf(createSystemMessage())
        conversationStore.save(initialConversation.map(conversationMapper::toStoredMessage))
        return initialConversation
    }

    private fun saveConversation() {
        conversationStore.save(conversation.map(conversationMapper::toStoredMessage))
    }

    private fun createSystemMessage(): ChatMessage =
        ChatMessage(
            role = ChatRole.SYSTEM,
            content = systemPrompt
        )

    private fun effectiveConversation(): List<ChatMessage> =
        memoryStrategy.messagesForModel(conversation)
}
