package agent.memory.strategy

import agent.memory.core.MemoryStrategy
import agent.memory.strategy.branching.BranchingMemoryStrategy
import agent.memory.strategy.nocompression.NoCompressionMemoryStrategy
import agent.memory.strategy.slidingwindow.SlidingWindowMemoryStrategy
import agent.memory.strategy.stickyfacts.LlmConversationFactsExtractor
import agent.memory.strategy.stickyfacts.StickyFactsMemoryStrategy
import agent.memory.strategy.summary.LlmConversationSummarizer
import agent.memory.strategy.summary.SummaryCompressionMemoryStrategy
import llm.core.LanguageModel
import ui.cli.CliCommands

/**
 * Создаёт стратегии памяти, которые можно выбрать в рамках CLI-сессии.
 */
object MemoryStrategyFactory {
    private const val DEFAULT_RECENT_MESSAGES_COUNT = 2
    private const val DEFAULT_SUMMARY_BATCH_SIZE = 3
    private const val DEFAULT_FACTS_BATCH_SIZE = 3
    private val DEFAULT_STRATEGY_TYPE = MemoryStrategyType.NO_COMPRESSION

    /**
     * Возвращает список стратегий, доступных пользователю.
     */
    fun availableOptions(): List<MemoryStrategyOption> =
        listOf(
            MemoryStrategyOption(
                type = MemoryStrategyType.NO_COMPRESSION,
                displayName = "Без сжатия",
                description = "Отправляет в модель всю историю как есть."
            ),
            MemoryStrategyOption(
                type = MemoryStrategyType.SUMMARY_COMPRESSION,
                displayName = "Сжатие через summary",
                description =
                    "Когда накопится минимум 5 сообщений диалога, сворачивает старую часть пачками по 3 сообщения " +
                        "и оставляет последние 2 сообщения вне summary.",
                specificPromptDescription =
                    "Provider prompt-токены включают не только основной запрос, " +
                        "но и внутренние вызовы на обновление summary."
            ),
            MemoryStrategyOption(
                type = MemoryStrategyType.SLIDING_WINDOW,
                displayName = "Скользящее окно",
                description = "Всегда оставляет в контексте системные сообщения и только последние 2 сообщения диалога."
            ),
            MemoryStrategyOption(
                type = MemoryStrategyType.STICKY_FACTS,
                displayName = "Sticky Facts",
                description =
                    "Пачками обновляет facts после накопления 3 новых сообщений пользователя " +
                        "(цель, ограничения, предпочтения, решения, договорённости) " +
                        "и отправляет их в модель вместе с последними 2 сообщениями.",
                specificPromptDescription =
                    "Provider prompt-токены включают не только основной запрос, " +
                        "но и внутренние вызовы на извлечение и обновление facts."
            ),
            MemoryStrategyOption(
                type = MemoryStrategyType.BRANCHING,
                displayName = "Ветки диалога",
                description =
                    "Позволяет сохранять checkpoint, создавать несколько независимых веток " +
                        "и переключаться между ними в рамках одной модели.",
                additionalCommands = listOf(
                    MemoryStrategyCommandHelp(
                        command = "${CliCommands.CHECKPOINT} [name]",
                        description = "Создаёт checkpoint текущего состояния диалога."
                    ),
                    MemoryStrategyCommandHelp(
                        command = CliCommands.BRANCHES,
                        description = "Показывает активную ветку, последний checkpoint и список веток."
                    ),
                    MemoryStrategyCommandHelp(
                        command = "${CliCommands.BRANCH} create <name>",
                        description = "Создаёт новую ветку от последнего checkpoint."
                    ),
                    MemoryStrategyCommandHelp(
                        command = "${CliCommands.BRANCH} use <name>",
                        description = "Переключает диалог на выбранную ветку."
                    )
                )
            )
        )

    /**
     * Возвращает стратегию памяти по умолчанию для запуска CLI-сессии.
     */
    fun defaultOption(): MemoryStrategyOption =
        availableOptions().first { it.type == DEFAULT_STRATEGY_TYPE }

    /**
     * Создаёт экземпляр стратегии для выбранной опции.
     */
    fun create(
        strategyType: MemoryStrategyType,
        languageModel: LanguageModel
    ): MemoryStrategy =
        when (strategyType) {
            MemoryStrategyType.NO_COMPRESSION -> NoCompressionMemoryStrategy()
            MemoryStrategyType.SUMMARY_COMPRESSION -> SummaryCompressionMemoryStrategy(
                recentMessagesCount = DEFAULT_RECENT_MESSAGES_COUNT,
                summaryBatchSize = DEFAULT_SUMMARY_BATCH_SIZE,
                summarizer = LlmConversationSummarizer(languageModel)
            )
            MemoryStrategyType.SLIDING_WINDOW -> SlidingWindowMemoryStrategy(
                recentMessagesCount = DEFAULT_RECENT_MESSAGES_COUNT
            )
            MemoryStrategyType.STICKY_FACTS -> StickyFactsMemoryStrategy(
                recentMessagesCount = DEFAULT_RECENT_MESSAGES_COUNT,
                factsBatchSize = DEFAULT_FACTS_BATCH_SIZE,
                factsExtractor = LlmConversationFactsExtractor(languageModel)
            )
            MemoryStrategyType.BRANCHING -> BranchingMemoryStrategy()
        }
}

/**
 * Пользовательское описание стратегии памяти, доступной для выбора.
 *
 * @property type доменный тип стратегии.
 * @property displayName имя стратегии в пользовательском интерфейсе.
 * @property description краткое описание поведения стратегии.
 * @property specificPromptDescription дополнительное пояснение к provider prompt-токенам
 * для стратегий с внутренними LLM-вызовами.
 * @property additionalCommands список дополнительных команд стратегии и их назначений.
 */
data class MemoryStrategyOption(
    val type: MemoryStrategyType,
    val displayName: String,
    val description: String,
    val specificPromptDescription: String? = null,
    val additionalCommands: List<MemoryStrategyCommandHelp> = emptyList()
) {
    /**
     * Устойчивый строковый идентификатор стратегии для CLI, storage и отчётов.
     */
    val id: String
        get() = type.id
}

/**
 * Справка по дополнительной CLI-команде, связанной со стратегией памяти.
 *
 * @property command синтаксис команды.
 * @property description краткое пояснение назначения команды.
 */
data class MemoryStrategyCommandHelp(
    val command: String,
    val description: String
)


