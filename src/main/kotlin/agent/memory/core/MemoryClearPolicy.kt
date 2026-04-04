package agent.memory.core

import agent.memory.model.MemoryState
import agent.memory.model.ShortTermMemory
import agent.memory.model.WorkingMemory
import llm.core.model.ChatMessage

/**
 * Определяет, какие слои памяти нужно очистить при reset текущего контекста.
 */
interface MemoryClearPolicy {
    /**
     * Возвращает новое состояние памяти после очистки.
     *
     * @param state текущее состояние layered memory.
     * @param systemMessage системное сообщение, которое должно остаться в short-term слое.
     * @param memoryStrategy активная short-term стратегия, которой нужно пересчитать derived state.
     * @return состояние памяти после применения clear policy.
     */
    fun clear(state: MemoryState, systemMessage: ChatMessage, memoryStrategy: MemoryStrategy): MemoryState
}

/**
 * Сбрасывает short-term и working память текущей задачи, сохраняя long-term слой.
 */
class TaskScopedMemoryClearPolicy : MemoryClearPolicy {
    /**
     * Пересобирает short-term слой от одного system prompt, очищает working память и сохраняет long-term заметки.
     *
     * @param state текущее состояние layered memory.
     * @param systemMessage системное сообщение, которое должно остаться в short-term слое.
     * @param memoryStrategy активная short-term стратегия.
     * @return состояние после очистки short-term и working памяти.
     */
    override fun clear(state: MemoryState, systemMessage: ChatMessage, memoryStrategy: MemoryStrategy): MemoryState =
        memoryStrategy.refreshState(
            state.copy(
                shortTerm = ShortTermMemory(messages = listOf(systemMessage)),
                working = WorkingMemory()
            ),
            MemoryStateRefreshMode.REGULAR
        )
}
