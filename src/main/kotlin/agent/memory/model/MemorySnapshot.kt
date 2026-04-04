package agent.memory.model

import agent.memory.strategy.MemoryStrategyType

/**
 * Снимок памяти для inspection/use cases, где важно знать не только данные памяти,
 * но и реально активную short-term стратегию.
 *
 * @property state текущее layered memory state.
 * @property shortTermStrategyType реально активная short-term стратегия менеджера памяти.
 */
data class MemorySnapshot(
    val state: MemoryState,
    val shortTermStrategyType: MemoryStrategyType
)
