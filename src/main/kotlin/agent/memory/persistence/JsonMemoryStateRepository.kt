package agent.memory.persistence

import agent.memory.model.MemoryState
import agent.storage.ConversationStore
import agent.storage.JsonConversationStore
import java.nio.file.Path
import llm.core.LanguageModel

/**
 * JSON-реализация репозитория памяти поверх persisted conversation store.
 *
 * @param conversationStore основное хранилище состояния памяти.
 * @param stateMapper mapper между runtime- и storage-моделью.
 * @param createStore фабрика store для импорта из произвольного пути.
 */
class JsonMemoryStateRepository(
    private val conversationStore: ConversationStore,
    private val stateMapper: ConversationMemoryStateMapper = ConversationMemoryStateMapper(),
    private val createStore: (Path) -> ConversationStore = ::JsonConversationStore
) : MemoryStateRepository {
    /**
     * Загружает runtime-состояние из основного JSON-хранилища.
     *
     * @return сохранённое runtime-состояние памяти.
     */
    override fun load(): MemoryState =
        stateMapper.toRuntime(conversationStore.loadState())

    /**
     * Сохраняет runtime-состояние в основное JSON-хранилище.
     *
     * @param state актуальное runtime-состояние памяти.
     */
    override fun save(state: MemoryState) {
        conversationStore.saveState(stateMapper.toStored(state))
    }

    /**
     * Загружает runtime-состояние из указанного JSON-файла.
     *
     * @param sourcePath путь к persisted state.
     * @return импортированное runtime-состояние памяти.
     */
    override fun loadFrom(sourcePath: Path): MemoryState =
        stateMapper.toRuntime(createStore(sourcePath).loadState())

    companion object {
        /**
         * Создаёт JSON-репозиторий памяти для текущей модели.
         *
         * @param languageModel активная языковая модель.
         * @return репозиторий с файловым JSON-хранилищем.
         */
        fun forLanguageModel(languageModel: LanguageModel): JsonMemoryStateRepository =
            JsonMemoryStateRepository(JsonConversationStore.forLanguageModel(languageModel))
    }
}
