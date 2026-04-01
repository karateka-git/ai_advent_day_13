package agent.memory.core

import agent.core.AgentTokenStats
import agent.core.BranchCheckpointInfo
import agent.core.BranchInfo
import agent.core.BranchingStatus
import java.nio.file.Path
import llm.core.model.ChatMessage

/**
 * Фасад, через который агент управляет сохранённым состоянием диалога и эффективным prompt.
 */
interface MemoryManager {
    /**
     * Возвращает исходный диалог, который сейчас хранится в памяти.
     */
    fun currentConversation(): List<ChatMessage>

    /**
     * Оценивает расход токенов для гипотетического следующего пользовательского сообщения без
     * изменения состояния.
     */
    fun previewTokenStats(userPrompt: String): AgentTokenStats

    /**
     * Добавляет новое пользовательское сообщение и возвращает эффективный контекст для модели.
     */
    fun appendUserMessage(userPrompt: String): List<ChatMessage>

    /**
     * Сохраняет ответ ассистента после завершения запроса к модели.
     */
    fun appendAssistantMessage(content: String)

    /**
     * Очищает видимый пользователю контекст, сохраняя базовый системный prompt.
     */
    fun clear()

    /**
     * Заменяет текущее содержимое памяти данными, загруженными из указанного файла.
     */
    fun replaceContextFromFile(sourcePath: Path)

    /**
     * Создаёт checkpoint из текущей активной ветки.
     */
    fun createCheckpoint(name: String? = null): BranchCheckpointInfo

    /**
     * Создаёт новую ветку из последнего checkpoint.
     */
    fun createBranch(name: String): BranchInfo

    /**
     * Переключает активную ветку.
     */
    fun switchBranch(name: String): BranchInfo

    /**
     * Возвращает состояние ветвления для текущего диалога.
     */
    fun branchStatus(): BranchingStatus
}


