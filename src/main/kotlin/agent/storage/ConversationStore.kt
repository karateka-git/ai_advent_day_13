package agent.storage

import agent.storage.model.ConversationMemoryState

/**
 * Низкоуровневый storage-контракт для persisted memory state.
 */
interface ConversationStore {
    /**
     * Загружает полное состояние памяти диалога из хранилища.
     *
     * @return сохранённое состояние памяти, включая short-term, working, long-term и strategy-specific state.
     */
    fun loadState(): ConversationMemoryState

    /**
     * Сохраняет полное состояние памяти диалога в хранилище.
     *
     * @param state состояние памяти, которое нужно записать.
     */
    fun saveState(state: ConversationMemoryState)
}
