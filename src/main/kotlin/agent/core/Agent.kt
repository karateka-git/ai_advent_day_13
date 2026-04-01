package agent.core

import agent.format.ResponseFormat
import java.nio.file.Path

interface Agent<T> {
    val info: AgentInfo
    val responseFormat: ResponseFormat<T>

    /**
     * Возвращает предварительную статистику токенов для пользовательского запроса
     * без фактической отправки запроса в языковую модель.
     *
     * @param userPrompt текст нового сообщения пользователя
     * @return локально рассчитанная статистика токенов для текущего контекста и нового сообщения
     */
    fun previewTokenStats(userPrompt: String): AgentTokenStats

    /**
     * Отправляет пользовательский запрос агенту и возвращает ответ в целевом формате.
     *
     * @param userPrompt текст нового сообщения пользователя
     * @return ответ агента вместе со статистикой токенов
     */
    fun ask(userPrompt: String): AgentResponse<T>

    /**
     * Очищает текущий контекст диалога, сохраняя базовое системное сообщение.
     */
    fun clearContext()

    /**
     * Заменяет текущий контекст диалога сообщениями из указанного файла.
     *
     * @param sourcePath путь к файлу с сохранённой историей диалога
     */
    fun replaceContextFromFile(sourcePath: Path)

    /**
     * Создаёт checkpoint из текущего активного состояния диалога.
     */
    fun createCheckpoint(name: String? = null): BranchCheckpointInfo

    /**
     * Создаёт новую ветку из последнего checkpoint.
     */
    fun createBranch(name: String): BranchInfo

    /**
     * Переключает активную ветку диалога.
     */
    fun switchBranch(name: String): BranchInfo

    /**
     * Возвращает текущее состояние ветвления диалога.
     */
    fun branchStatus(): BranchingStatus
}

