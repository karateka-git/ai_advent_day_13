package agent.memory.core

import agent.capability.AgentCapability
import agent.core.AgentTokenStats
import agent.memory.model.MemorySnapshot
import agent.memory.model.MemoryState
import java.nio.file.Path
import llm.core.model.ChatMessage

/**
 * Фасад для управления layered memory и эффективным prompt агента.
 */
interface MemoryManager {
    /**
     * Возвращает полный short-term диалог без prompt assembly.
     *
     * @return список сообщений short-term слоя.
     */
    fun currentConversation(): List<ChatMessage>

    /**
     * Оценивает локальный расход токенов для следующего пользовательского сообщения.
     *
     * @param userPrompt текст нового пользовательского сообщения.
     * @return локально рассчитанная статистика токенов.
     */
    fun previewTokenStats(userPrompt: String): AgentTokenStats

    /**
     * Добавляет пользовательское сообщение в short-term память, перераспределяет заметки по слоям
     * и возвращает эффективный prompt для модели.
     *
     * @param userPrompt текст нового пользовательского сообщения.
     * @return итоговый контекст, который должен быть отправлен в модель.
     */
    fun appendUserMessage(userPrompt: String): List<ChatMessage>

    /**
     * Сохраняет ответ ассистента и обновляет derived state активной short-term стратегии.
     *
     * @param content текст ответа ассистента.
     */
    fun appendAssistantMessage(content: String)

    /**
     * Очищает short-term память, сохраняя системное сообщение.
     */
    fun clear()

    /**
     * Заменяет текущее состояние памяти содержимым из указанного JSON-файла.
     *
     * @param sourcePath путь к persisted state.
     */
    fun replaceContextFromFile(sourcePath: Path)

    /**
     * Возвращает полный runtime-снимок layered memory.
     *
     * @return текущее состояние памяти.
     */
    fun memoryState(): MemoryState

    /**
     * Возвращает inspection-снимок памяти вместе с реально активной short-term стратегией.
     *
     * @return snapshot памяти для UI и отладочного вывода.
     */
    fun memorySnapshot(): MemorySnapshot

    /**
     * Возвращает capability активной стратегии памяти, если она поддерживается.
     *
     * @param capabilityType ожидаемый тип capability.
     * @return capability или `null`, если она недоступна.
     */
    fun <TCapability : AgentCapability> capability(capabilityType: Class<TCapability>): TCapability?
}
