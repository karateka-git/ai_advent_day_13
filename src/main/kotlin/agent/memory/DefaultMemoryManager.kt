package agent.memory

import agent.core.AgentTokenStats
import agent.storage.JsonConversationStore
import agent.storage.mapper.ChatMessageConversationMapper
import agent.storage.model.ConversationMemoryState
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
    private var memoryState = conversationStore.loadState()
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
        val importedMessages = JsonConversationStore(sourcePath).loadState().messages
            .map(conversationMapper::fromStoredMessage)

        require(importedMessages.isNotEmpty()) {
            "Файл истории $sourcePath пустой или не содержит сообщений."
        }

        conversation.clear()
        conversation += importedMessages
        saveConversation()
    }

    private fun loadConversation(): List<ChatMessage> {
        val savedConversation = memoryState.messages
            .map(conversationMapper::fromStoredMessage)
        if (savedConversation.isNotEmpty()) {
            return savedConversation
        }

        val initialConversation = listOf(createSystemMessage())
        saveConversation(initialConversation)
        return initialConversation
    }

    private fun saveConversation() {
        saveConversation(conversation)
    }

    private fun saveConversation(messages: List<ChatMessage>) {
        memoryState = memoryState.copy(
            messages = messages.map(conversationMapper::toStoredMessage)
        )
        conversationStore.saveState(memoryState)
    }

    private fun createSystemMessage(): ChatMessage =
        ChatMessage(
            role = ChatRole.SYSTEM,
            content = systemPrompt
        )

    private fun effectiveConversation(): List<ChatMessage> =
        memoryStrategy.messagesForModel(conversation)
}
