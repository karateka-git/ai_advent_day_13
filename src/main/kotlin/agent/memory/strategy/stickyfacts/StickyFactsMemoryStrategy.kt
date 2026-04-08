package agent.memory.strategy.stickyfacts

import agent.memory.core.MemoryStateRefreshMode
import agent.memory.core.MemoryStrategy
import agent.memory.model.MemoryState
import agent.memory.model.ShortTermMemory
import agent.memory.model.StickyFactsStrategyState
import agent.memory.strategy.MemoryStrategyType
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

/**
 * Стратегия памяти, которая хранит устойчивые facts и добавляет их в prompt
 * вместе с последними сообщениями диалога.
 *
 * Переопределяет `refreshState`, потому что должна пакетно обновлять facts и отслеживать,
 * какая часть диалога уже обработана.
 */
class StickyFactsMemoryStrategy(
    private val recentMessagesCount: Int,
    private val factsBatchSize: Int,
    private val factsExtractor: ConversationFactsExtractor
) : MemoryStrategy {
    init {
        require(recentMessagesCount > 0) {
            "Количество последних сообщений должно быть больше нуля."
        }
        require(factsBatchSize > 0) {
            "Размер батча для обновления facts должен быть больше нуля."
        }
    }

    override val type: MemoryStrategyType = MemoryStrategyType.STICKY_FACTS

    override fun effectiveContext(state: MemoryState): List<ChatMessage> =
        state.shortTerm.derivedMessages.toList()

    override fun refreshState(
        state: MemoryState,
        mode: MemoryStateRefreshMode
    ): MemoryState {
        if (mode == MemoryStateRefreshMode.PREVIEW) {
            return rebuildDerivedMessages(state)
        }

        val lastMessage = state.shortTerm.rawMessages.lastOrNull()
        if (lastMessage?.role != ChatRole.USER) {
            return rebuildDerivedMessages(state)
        }

        val factsState = stickyFactsState(state)
        val existingFacts = factsState?.facts.orEmpty()
        val dialogMessages = state.shortTerm.rawMessages
        val coveredMessagesCount = factsState?.coveredMessagesCount ?: 0
        val newMessagesBatch = dialogMessages.drop(coveredMessagesCount)
        val newUserMessagesCount = newMessagesBatch.count { it.role == ChatRole.USER }

        if (newUserMessagesCount < factsBatchSize) {
            return rebuildDerivedMessages(
                state.copy(
                    shortTerm = state.shortTerm.copy(
                        strategyState = StickyFactsStrategyState(
                            facts = existingFacts,
                            coveredMessagesCount = coveredMessagesCount
                        )
                    )
                )
            )
        }

        val updatedFacts = factsExtractor.extract(
            existingFacts = existingFacts,
            newMessagesBatch = newMessagesBatch
        )

        return rebuildDerivedMessages(
            state.copy(
                shortTerm = state.shortTerm.copy(
                    strategyState = StickyFactsStrategyState(
                        facts = updatedFacts,
                        coveredMessagesCount = dialogMessages.size
                    )
                )
            )
        )
    }

    private fun stickyFactsState(state: MemoryState): StickyFactsStrategyState? =
        (state.shortTerm.strategyState as? StickyFactsStrategyState)
            ?.takeIf { it.strategyType == type }

    private fun toFactsMessage(facts: Map<String, String>): ChatMessage =
        ChatMessage(
            role = ChatRole.SYSTEM,
            content = buildString {
                appendLine("Важные facts из диалога:")
                facts.entries.sortedBy { it.key }.forEach { (key, value) ->
                    appendLine("- $key: $value")
                }
            }.trimEnd()
        )

    private fun rebuildDerivedMessages(state: MemoryState): MemoryState {
        val rawMessages = state.shortTerm.rawMessages
        val factsState = stickyFactsState(state)

        val derivedMessages = buildList {
            factsState?.facts
                ?.takeIf { it.isNotEmpty() }
                ?.let(::toFactsMessage)
                ?.let(::add)
            addAll(rawMessages.takeLast(recentMessagesCount))
        }

        return state.copy(
            shortTerm = ShortTermMemory(
                rawMessages = rawMessages,
                derivedMessages = derivedMessages,
                strategyState = state.shortTerm.strategyState
            )
        )
    }
}
