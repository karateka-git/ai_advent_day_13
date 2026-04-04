package agent.memory.core

import agent.lifecycle.ContextCompressionStats
import agent.memory.model.MemoryState
import agent.memory.model.SummaryStrategyState
import agent.memory.strategy.MemoryStrategyType

/**
 * Определяет, произошло ли значимое сжатие контекста после refresh short-term стратегии.
 */
interface MemoryCompressionObserver {
    /**
     * Возвращает статистику compression, если очередное обновление состояния действительно сократило контекст.
     *
     * @param previousState состояние до refresh.
     * @param refreshedState состояние после refresh.
     * @param countTokens функция расчёта числа токенов для произвольного состояния памяти.
     * @return статистика compression или `null`, если сжатия не было.
     */
    fun buildStats(
        previousState: MemoryState,
        refreshedState: MemoryState,
        countTokens: (MemoryState) -> Int?
    ): ContextCompressionStats?
}

/**
 * Observer для summary-based compression, который отслеживает рост covered messages.
 */
class SummaryBasedMemoryCompressionObserver : MemoryCompressionObserver {
    /**
     * Возвращает статистику только если rolling summary покрыло больше сообщений, чем раньше.
     *
     * @param previousState состояние до refresh.
     * @param refreshedState состояние после refresh.
     * @param countTokens функция подсчёта токенов для assembled prompt.
     * @return статистика compression или `null`, если coverage summary не выросло.
     */
    override fun buildStats(
        previousState: MemoryState,
        refreshedState: MemoryState,
        countTokens: (MemoryState) -> Int?
    ): ContextCompressionStats? {
        if (summaryCoveredMessagesCount(refreshedState) <= summaryCoveredMessagesCount(previousState)) {
            return null
        }

        return ContextCompressionStats(
            tokensBefore = countTokens(previousState),
            tokensAfter = countTokens(refreshedState)
        )
    }

    private fun summaryCoveredMessagesCount(state: MemoryState): Int =
        (state.shortTerm.strategyState as? SummaryStrategyState)
            ?.takeIf { it.strategyType == MemoryStrategyType.SUMMARY_COMPRESSION }
            ?.coveredMessagesCount
            ?: 0
}
