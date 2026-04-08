package agent.task.persistence

import agent.task.model.TaskState
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
import llm.core.LanguageModel

private const val TASK_CONTEXT_DIRECTORY = "config/tasks"

/**
 * JSON-репозиторий одной текущей conversation-scoped задачи.
 *
 * Task subsystem хранится отдельно от memory subsystem, но использует похожую
 * модель привязки к текущей паре провайдер/модель.
 */
class JsonTaskStateRepository(
    private val storagePath: Path
) : TaskStateRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    override fun load(): TaskState? {
        if (!Files.exists(storagePath)) {
            return null
        }

        val rawContent = Files.readString(storagePath)
        if (rawContent.isBlank()) {
            return null
        }

        return json.decodeFromString<TaskState>(rawContent)
    }

    override fun save(state: TaskState) {
        val parent = storagePath.parent
        if (parent != null) {
            Files.createDirectories(parent)
        }

        Files.writeString(
            storagePath,
            json.encodeToString(TaskState.serializer(), state)
        )
    }

    override fun clear() {
        Files.deleteIfExists(storagePath)
    }

    companion object {
        /**
         * Создаёт репозиторий, привязанный к файлу состояния конкретной модели.
         *
         * @param languageModel активная языковая модель.
         * @return JSON-репозиторий task state для этой модели.
         */
        fun forLanguageModel(languageModel: LanguageModel): JsonTaskStateRepository =
            JsonTaskStateRepository(buildStoragePath(languageModel))

        /**
         * Строит путь к JSON-файлу task state для пары провайдер/модель.
         *
         * @param languageModel активная языковая модель.
         * @return путь к JSON-файлу task subsystem.
         */
        internal fun buildStoragePath(languageModel: LanguageModel): Path {
            val providerPart = sanitizePathPart(languageModel.info.name)
            val modelPart = sanitizePathPart(languageModel.info.model)
            return Path.of(TASK_CONTEXT_DIRECTORY, "${providerPart}__${modelPart}.json")
        }

        /**
         * Преобразует имя провайдера или модели в безопасный сегмент пути.
         *
         * @param value исходное имя.
         * @return безопасный сегмент пути.
         */
        private fun sanitizePathPart(value: String): String =
            value
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .ifBlank { "default" }
    }
}
