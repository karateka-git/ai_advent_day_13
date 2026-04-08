package agent.prompt

import llm.core.model.ChatMessage
import llm.core.model.ChatRole

/**
 * Собирает финальный prompt агента из базового system prompt, contribution-блоков подсистем и
 * runtime-истории сообщений.
 *
 * Это единственное место, где создаётся итоговый system message для модели.
 */
class AgentPromptAssembler {
    /**
     * Собирает итоговый system prompt из базового prompt и contribution-блоков подсистем.
     *
     * @param baseSystemPrompt базовый system prompt агента.
     * @param contributions текстовые блоки от memory, task и других источников.
     * @return финальный system prompt.
     */
    fun assembleSystemPrompt(baseSystemPrompt: String, contributions: List<String>): String =
        buildString {
            append(baseSystemPrompt.trim())
            contributions
                .map(String::trim)
                .filter(String::isNotEmpty)
                .forEach { contribution ->
                    append("\n\n")
                    append(contribution)
                }
        }

    /**
     * Собирает итоговый conversation для модели.
     *
     * Базовый system prompt агента не ожидается внутри `messages`: он всегда создаётся здесь и
     * добавляется в начало conversation поверх runtime-сообщений.
     *
     * @param baseSystemPrompt базовый system prompt агента.
     * @param messages история сообщений без базового system prompt агента.
     * @param contributions текстовые блоки от memory, task и других источников.
     * @return итоговый список сообщений для LLM.
     */
    fun assembleConversation(
        baseSystemPrompt: String,
        messages: List<ChatMessage>,
        contributions: List<String>
    ): List<ChatMessage> {
        val finalSystemPrompt = assembleSystemPrompt(baseSystemPrompt, contributions)
        return listOf(ChatMessage(role = ChatRole.SYSTEM, content = finalSystemPrompt)) + messages
    }
}
