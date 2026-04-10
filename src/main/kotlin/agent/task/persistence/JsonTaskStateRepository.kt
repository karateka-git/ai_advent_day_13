package agent.task.persistence

import agent.task.model.TaskSessionState
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
import llm.core.LanguageModel

private const val TASK_CONTEXT_DIRECTORY = "config/tasks"

/**
 * JSON-репозиторий полного conversation-scoped task session state.
 *
 * Task subsystem хранится отдельно от memory subsystem, но использует похожую
 * модель привязки к текущей паре провайдер/модель.
 */
class JsonTaskStateRepository(
    private val storagePath: Path
) : TaskSessionStateRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    override fun load(): TaskSessionState? {
        if (!Files.exists(storagePath)) {
            return null
        }

        val rawContent = Files.readString(storagePath)
        if (rawContent.isBlank()) {
            return null
        }

        return json.decodeFromString<TaskSessionState>(rawContent)
    }

    override fun save(state: TaskSessionState) {
        val parent = storagePath.parent
        if (parent != null) {
            Files.createDirectories(parent)
        }

        Files.writeString(
            storagePath,
            json.encodeToString(TaskSessionState.serializer(), state)
        )
    }

    override fun clear() {
        Files.deleteIfExists(storagePath)
    }

    companion object {
        /**
         * Создаёт репозиторий, привязанный к файлу состояния конкретной модели.
         */
        fun forLanguageModel(languageModel: LanguageModel): JsonTaskStateRepository =
            JsonTaskStateRepository(buildStoragePath(languageModel))

        /**
         * Строит путь к JSON-файлу task state для пары провайдер/модель.
         */
        internal fun buildStoragePath(languageModel: LanguageModel): Path {
            val providerPart = sanitizePathPart(languageModel.info.name)
            val modelPart = sanitizePathPart(languageModel.info.model)
            return Path.of(TASK_CONTEXT_DIRECTORY, "${providerPart}__${modelPart}.json")
        }

        private fun sanitizePathPart(value: String): String =
            value
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .ifBlank { "default" }
    }
}
