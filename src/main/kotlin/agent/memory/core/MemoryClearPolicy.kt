package agent.memory.core

import agent.memory.model.MemoryState
import agent.memory.model.PendingMemoryState
import agent.memory.model.ShortTermMemory
import agent.memory.model.WorkingMemory

/**
 * Определяет, какие слои памяти нужно очистить при reset текущего контекста.
 */
interface MemoryClearPolicy {
    /**
     * Возвращает новое состояние памяти после очистки.
     *
     * @param state текущее состояние layered memory.
     * @param memoryStrategy активная short-term стратегия, которой нужно пересчитать derived state.
     * @return состояние памяти после применения clear policy.
     */
    fun clear(state: MemoryState, memoryStrategy: MemoryStrategy): MemoryState
}

/**
 * Очищает runtime-журнал short-term слоя, working память и pending-очередь, сохраняя long-term
 * заметки.
 */
class TaskScopedMemoryClearPolicy : MemoryClearPolicy {
    /**
     * Очищает short-term runtime-журнал, working память и pending-очередь, а затем пересчитывает
     * derived state через активную short-term стратегию.
     *
     * @param state текущее состояние layered memory.
     * @param memoryStrategy активная short-term стратегия.
     * @return состояние после очистки short-term и working памяти.
     */
    override fun clear(state: MemoryState, memoryStrategy: MemoryStrategy): MemoryState =
        memoryStrategy.refreshState(
            state.copy(
                shortTerm = ShortTermMemory(),
                working = WorkingMemory(),
                pending = PendingMemoryState()
            ),
            MemoryStateRefreshMode.REGULAR
        )
}
