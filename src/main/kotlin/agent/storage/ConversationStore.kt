package agent.storage

import agent.storage.model.StoredMessage
import agent.storage.model.ConversationMemoryState

interface ConversationStore {
    /**
     * Загружает полное состояние памяти диалога из хранилища.
     *
     * @return сохранённое состояние памяти, включая сообщения и служебные данные
     */
    fun loadState(): ConversationMemoryState

    /**
     * Сохраняет полное состояние памяти диалога в хранилище.
     *
     * @param state состояние памяти, которое нужно записать
     */
    fun saveState(state: ConversationMemoryState)

    /**
     * Загружает сохранённую историю диалога из хранилища.
     *
     * @return список сохранённых сообщений
     */
    fun load(): List<StoredMessage> = loadState().messages

    /**
     * Сохраняет переданный набор сообщений в хранилище.
     *
     * @param messages сообщения, которые нужно записать
     */
    fun save(messages: List<StoredMessage>) {
        saveState(ConversationMemoryState(messages = messages))
    }
}
