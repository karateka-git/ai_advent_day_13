package agent.storage

import agent.storage.model.StoredMessage

interface ConversationStore {
    /**
     * Загружает сохранённую историю диалога из хранилища.
     *
     * @return список сохранённых сообщений
     */
    fun load(): List<StoredMessage>

    /**
     * Сохраняет переданный набор сообщений в хранилище.
     *
     * @param messages сообщения, которые нужно записать
     */
    fun save(messages: List<StoredMessage>)
}
