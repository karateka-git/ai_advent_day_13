package bootstrap

import agent.core.Agent
import agent.impl.MrAgent
import agent.lifecycle.AgentLifecycleListener
import agent.memory.layer.MemoryLayerAllocatorFactory
import agent.memory.strategy.MemoryStrategyFactory
import agent.memory.strategy.MemoryStrategyType
import agent.task.core.DefaultTaskManager
import agent.task.persistence.JsonTaskStateRepository
import java.net.http.HttpClient
import java.util.Properties
import llm.core.LanguageModel

/**
 * Собирает агента приложения вместе с его memory-подсистемой.
 *
 * Позволяет держать детали выбора allocator'а и утилитных моделей вне `Main.kt`.
 */
class AgentFactory(
    private val config: Properties,
    private val httpClient: HttpClient
) {
    /**
     * Создаёт агента для выбранной основной модели и стратегии памяти.
     *
     * Для durable memory allocator при необходимости поднимается отдельная утилитная модель,
     * независимая от основной модели диалога.
     *
     * @param languageModel основная модель диалога.
     * @param lifecycleListener обработчик lifecycle-событий агента.
     * @param strategyType выбранная стратегия short-term памяти.
     * @return готовый агент.
     */
    fun create(
        languageModel: LanguageModel,
        lifecycleListener: AgentLifecycleListener,
        strategyType: MemoryStrategyType
    ): Agent<String> {
        val allocator = MemoryLayerAllocatorFactory.create(
            config = config,
            httpClient = httpClient
        )

        return MrAgent(
            languageModel = languageModel,
            lifecycleListener = lifecycleListener,
            memoryStrategy = MemoryStrategyFactory.create(
                strategyType = strategyType,
                languageModel = languageModel
            ),
            memoryLayerAllocator = allocator,
            taskManager = DefaultTaskManager(
                repository = JsonTaskStateRepository.forLanguageModel(languageModel)
            )
        )
    }
}
