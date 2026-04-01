package agent.memory.strategy.facts

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import llm.core.LanguageModel
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

/**
 * LLM-экстрактор устойчивых facts для стратегии sticky facts.
 */
class LlmConversationFactsExtractor(
    private val languageModel: LanguageModel,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : ConversationFactsExtractor {
    override fun extract(
        existingFacts: Map<String, String>,
        newMessagesBatch: List<ChatMessage>
    ): Map<String, String> {
        val response = languageModel.complete(
            listOf(
                ChatMessage(ChatRole.SYSTEM, buildSystemPrompt()),
                ChatMessage(ChatRole.USER, buildUserPrompt(existingFacts, newMessagesBatch))
            )
        )

        val payload = extractJsonObject(response.content)
        val parsed = json.decodeFromString<FactsExtractionPayload>(payload)

        return buildMap {
            putAll(existingFacts)
            parsed.facts
                .filterKeys { it.isNotBlank() }
                .mapValues { it.value.trim() }
                .filterValues { it.isNotBlank() }
                .forEach(::put)
        }
    }

    /**
     * Извлекает JSON-объект из ответа модели даже при markdown-обёртке.
     */
    fun extractJsonObject(rawContent: String): String {
        val fencedJson = Regex("```json\\s*(\\{[\\s\\S]*})\\s*```").find(rawContent)
        if (fencedJson != null) {
            return fencedJson.groupValues[1]
        }

        val fenced = Regex("```\\s*(\\{[\\s\\S]*})\\s*```").find(rawContent)
        if (fenced != null) {
            return fenced.groupValues[1]
        }

        val firstBrace = rawContent.indexOf('{')
        val lastBrace = rawContent.lastIndexOf('}')
        require(firstBrace >= 0 && lastBrace > firstBrace) {
            "LLM facts extractor не вернул JSON-объект."
        }

        return rawContent.substring(firstBrace, lastBrace + 1)
    }

    private fun buildSystemPrompt(): String =
        "Ты извлекаешь из диалога только устойчивые facts в формате ключ-значение. Верни только валидный JSON."

    private fun buildUserPrompt(
        existingFacts: Map<String, String>,
        newMessagesBatch: List<ChatMessage>
    ): String =
        buildString {
            appendLine("Обнови facts по новому батчу сообщений диалога.")
            appendLine("Сохраняй только устойчивые данные: цель, ограничения, предпочтения, решения, договорённости, интеграции, сроки, бюджет.")
            appendLine("Не добавляй временные или малозначимые детали.")
            appendLine("Если новый батч не даёт полезных facts, верни существующие без лишних изменений.")
            appendLine("Верни только JSON вида {\"facts\": {\"ключ\": \"значение\"}}.")
            appendLine()
            appendLine("Текущие facts:")
            appendLine(json.encodeToString(existingFacts))
            appendLine()
            appendLine("Новый батч сообщений для обработки:")
            appendLine(
                newMessagesBatch.joinToString(separator = "\n") { message ->
                    "${message.role.apiValue}: ${message.content}"
                }
            )
        }
}

@Serializable
private data class FactsExtractionPayload(
    val facts: Map<String, String> = emptyMap()
)


