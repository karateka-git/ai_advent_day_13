package agent.memory

import agent.core.AgentTokenStats
import java.nio.file.Path
import llm.core.model.ChatMessage

interface MemoryManager {
    /**
     * Возвращает текущее представление диалога, которое хранится в памяти менеджера.
     *
     * @return список сообщений текущего контекста
     */
    fun currentConversation(): List<ChatMessage>

    /**
     * Рассчитывает предварительную статистику токенов для нового пользовательского сообщения
     * без изменения состояния памяти.
     *
     * @param userPrompt текст нового сообщения пользователя
     * @return предварительная статистика токенов
     */
    fun previewTokenStats(userPrompt: String): AgentTokenStats

    /**
     * Добавляет в память новое сообщение пользователя и возвращает обновлённый контекст,
     * который можно передать в языковую модель.
     *
     * @param userPrompt текст нового сообщения пользователя
     * @return обновлённый список сообщений диалога
     */
    fun appendUserMessage(userPrompt: String): List<ChatMessage>

    /**
     * Добавляет в память ответ ассистента после завершения запроса к модели.
     *
     * @param content текст ответа ассистента
     */
    fun appendAssistantMessage(content: String)

    /**
     * Полностью очищает пользовательскую часть памяти и восстанавливает начальный контекст.
     */
    fun clear()

    /**
     * Заменяет текущее состояние памяти контекстом, загруженным из файла.
     *
     * @param sourcePath путь к файлу с историей диалога
     */
    fun replaceContextFromFile(sourcePath: Path)
}
