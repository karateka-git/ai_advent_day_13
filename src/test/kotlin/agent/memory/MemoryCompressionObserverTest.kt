package agent.memory

import agent.memory.core.SummaryBasedMemoryCompressionObserver
import agent.memory.model.ConversationSummary
import agent.memory.model.MemoryState
import agent.memory.model.ShortTermMemory
import agent.memory.model.SummaryStrategyState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MemoryCompressionObserverTest {
    private val observer = SummaryBasedMemoryCompressionObserver()

    @Test
    fun `returns stats when summary covers more messages`() {
        val previousState = summaryState(coveredMessagesCount = 0)
        val refreshedState = summaryState(coveredMessagesCount = 2)

        val stats = observer.buildStats(
            previousState = previousState,
            refreshedState = refreshedState,
            countTokens = { state ->
                if ((state.shortTerm.strategyState as SummaryStrategyState).coveredMessagesCount == 0) 120 else 80
            }
        )

        requireNotNull(stats)
        assertEquals(120, stats.tokensBefore)
        assertEquals(80, stats.tokensAfter)
        assertEquals(40, stats.savedTokens)
    }

    @Test
    fun `returns null when summary coverage did not grow`() {
        val previousState = summaryState(coveredMessagesCount = 2)
        val refreshedState = summaryState(coveredMessagesCount = 2)

        val stats = observer.buildStats(
            previousState = previousState,
            refreshedState = refreshedState,
            countTokens = { 100 }
        )

        assertNull(stats)
    }

    private fun summaryState(coveredMessagesCount: Int): MemoryState =
        MemoryState(
            shortTerm = ShortTermMemory(
                strategyState = SummaryStrategyState(
                    summary = ConversationSummary(
                        content = "summary",
                        coveredMessagesCount = coveredMessagesCount
                    ),
                    coveredMessagesCount = coveredMessagesCount
                )
            )
        )
}
