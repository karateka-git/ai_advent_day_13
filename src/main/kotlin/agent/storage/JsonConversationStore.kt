package agent.storage

import agent.storage.model.ConversationMemoryState
import agent.storage.model.ConversationHistory
import agent.storage.model.StoredMessage
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import llm.core.LanguageModel

private const val CONTEXT_DIRECTORY = "config/conversations"

class JsonConversationStore(
    private val storagePath: Path
) : ConversationStore {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    override fun loadState(): ConversationMemoryState {
        if (!Files.exists(storagePath)) {
            return ConversationMemoryState()
        }

        val rawContent = Files.readString(storagePath)
        if (rawContent.isBlank()) {
            return ConversationMemoryState()
        }

        return try {
            json.decodeFromString<ConversationMemoryState>(rawContent)
        } catch (_: SerializationException) {
            ConversationMemoryState(
                messages = json.decodeFromString<ConversationHistory>(rawContent).messages
            )
        }
    }

    override fun saveState(state: ConversationMemoryState) {
        val parent = storagePath.parent
        if (parent != null) {
            Files.createDirectories(parent)
        }

        Files.writeString(
            storagePath,
            json.encodeToString(state)
        )
    }

    companion object {
        fun forLanguageModel(languageModel: LanguageModel): JsonConversationStore =
            JsonConversationStore(buildStoragePath(languageModel))

        internal fun buildStoragePath(languageModel: LanguageModel): Path {
            val providerPart = sanitizePathPart(languageModel.info.name)
            val modelPart = sanitizePathPart(languageModel.info.model)
            return Path.of(CONTEXT_DIRECTORY, "${providerPart}__${modelPart}.json")
        }

        private fun sanitizePathPart(value: String): String =
            value
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .ifBlank { "default" }
    }
}
