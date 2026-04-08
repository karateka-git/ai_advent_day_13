package agent.memory.strategy.summary

import agent.memory.core.MemoryStateRefreshMode
import agent.memory.core.MemoryStrategy
import agent.memory.model.ConversationSummary
import agent.memory.model.MemoryState
import agent.memory.model.ShortTermMemory
import agent.memory.model.SummaryStrategyState
import agent.memory.strategy.MemoryStrategyType
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

/**
 * Стратегия памяти, которая хранит rolling summary поверх полной истории сообщений
 * и отправляет в модель summary вместе с ещё не покрытым хвостом диалога.
 *
 * Переопределяет `refreshState`, потому что должна пересчитывать rolling summary и количество
 * сообщений, уже покрытых этим summary.
 */
class SummaryCompressionMemoryStrategy(
    private val recentMessagesCount: Int,
    private val summaryBatchSize: Int,
    private val summarizer: ConversationSummarizer
) : MemoryStrategy {
    init {
        require(recentMessagesCount > 0) {
            "Количество последних сообщений должно быть больше нуля."
        }
        require(summaryBatchSize > 0) {
            "Размер пачки для summary должен быть больше нуля."
        }
    }

    override val type: MemoryStrategyType = MemoryStrategyType.SUMMARY_COMPRESSION

    override fun effectiveContext(state: MemoryState): List<ChatMessage> =
        state.shortTerm.derivedMessages.toList()

    override fun refreshState(
        state: MemoryState,
        mode: MemoryStateRefreshMode
    ): MemoryState {
        if (mode == MemoryStateRefreshMode.PREVIEW) {
            return rebuildDerivedMessages(state)
        }

        val preparedState = prepareStateForSummary(state)
        var currentState = preparedState

        while (true) {
            val rawMessages = currentState.shortTerm.rawMessages
            val uncompressedMessages = rawMessages.drop(coveredMessagesCount(currentState))
            val messagesEligibleForCompression = uncompressedMessages.dropLastSafe(recentMessagesCount)

            if (messagesEligibleForCompression.size < summaryBatchSize) {
                return rebuildDerivedMessages(currentState)
            }

            val nextBatch = messagesEligibleForCompression.take(summaryBatchSize)
            val updatedCoveredMessagesCount = coveredMessagesCount(currentState) + nextBatch.size
            val summaryContent = buildUpdatedSummary(summary(currentState), nextBatch)

            currentState = currentState.copy(
                shortTerm = currentState.shortTerm.copy(
                    strategyState = SummaryStrategyState(
                        summary = ConversationSummary(
                            content = summaryContent,
                            coveredMessagesCount = updatedCoveredMessagesCount
                        ),
                        coveredMessagesCount = updatedCoveredMessagesCount
                    )
                )
            )
        }
    }

    /**
     * При первом включении summary после другой стратегии начинает свёртку
     * только с текущего окна, а не со всей накопленной истории.
     */
    private fun prepareStateForSummary(state: MemoryState): MemoryState {
        if (summaryState(state) != null) {
            return state
        }

        val dialogMessages = state.shortTerm.rawMessages
        val activationWindowSize = recentMessagesCount + summaryBatchSize
        val coveredMessagesCount = (dialogMessages.size - activationWindowSize).coerceAtLeast(0)

        return state.copy(
            shortTerm = state.shortTerm.copy(
                strategyState = SummaryStrategyState(
                    coveredMessagesCount = coveredMessagesCount
                )
            )
        )
    }

    private fun rebuildDerivedMessages(state: MemoryState): MemoryState {
        val rawMessages = state.shortTerm.rawMessages
        val coveredMessagesCount = coveredMessagesCount(state)
        val uncompressedTail = rawMessages.drop(coveredMessagesCount)
        val summary = summary(state)
        val derivedMessages =
            if (summary == null && coveredMessagesCount == 0) {
                rawMessages
            } else {
                buildList {
                    summary?.let(::toSummaryMessage)?.let(::add)
                    addAll(uncompressedTail)
                }
            }

        return state.copy(
            shortTerm = ShortTermMemory(
                rawMessages = rawMessages,
                derivedMessages = derivedMessages,
                strategyState = state.shortTerm.strategyState
            )
        )
    }

    private fun coveredMessagesCount(state: MemoryState): Int =
        summaryState(state)?.summary?.coveredMessagesCount
            ?: summaryState(state)?.coveredMessagesCount
            ?: 0

    private fun summary(state: MemoryState): ConversationSummary? =
        summaryState(state)?.summary

    private fun summaryState(state: MemoryState): SummaryStrategyState? =
        (state.shortTerm.strategyState as? SummaryStrategyState)
            ?.takeIf { it.strategyType == type }

    private fun buildUpdatedSummary(
        existingSummary: ConversationSummary?,
        nextBatch: List<ChatMessage>
    ): String {
        val messagesForSummary = buildList {
            existingSummary?.let { summary ->
                add(
                    ChatMessage(
                        role = ChatRole.SYSTEM,
                        content = "Предыдущее резюме: ${summary.content}"
                    )
                )
            }
            addAll(nextBatch)
        }

        return summarizer.summarize(messagesForSummary)
    }

    private fun toSummaryMessage(summary: ConversationSummary): ChatMessage =
        ChatMessage(
            role = ChatRole.SYSTEM,
            content = "Краткое резюме предыдущего диалога:\n${summary.content}"
        )

    private fun <T> List<T>.dropLastSafe(count: Int): List<T> =
        if (count >= size) {
            emptyList()
        } else {
            dropLast(count)
        }
}
