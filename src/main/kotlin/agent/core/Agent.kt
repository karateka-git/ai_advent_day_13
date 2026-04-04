package agent.core

import agent.capability.AgentCapability
import agent.format.ResponseFormat
import agent.memory.model.MemorySnapshot
import java.nio.file.Path

/**
 * Базовый контракт агента, с которым взаимодействует application- и UI-слой.
 *
 * @param T тип ответа после преобразования provider-ответа в пользовательский формат.
 */
interface Agent<T> {
    val info: AgentInfo
    val responseFormat: ResponseFormat<T>

    /**
     * Оценивает локальную стоимость следующего запроса без фактического вызова модели.
     *
     * @param userPrompt текст нового пользовательского сообщения.
     * @return локально рассчитанная статистика токенов для текущего контекста и нового prompt.
     */
    fun previewTokenStats(userPrompt: String): AgentTokenStats

    /**
     * Отправляет пользовательский prompt агенту и возвращает ответ в целевом формате.
     *
     * @param userPrompt текст нового пользовательского сообщения.
     * @return ответ агента вместе со статистикой токенов.
     */
    fun ask(userPrompt: String): AgentResponse<T>

    /**
     * Очищает текущий пользовательский контекст, сохраняя базовый системный prompt.
     */
    fun clearContext()

    /**
     * Заменяет текущее состояние памяти содержимым из указанного файла.
     *
     * @param sourcePath путь к JSON-файлу с сохранённым состоянием памяти.
     */
    fun replaceContextFromFile(sourcePath: Path)

    /**
     * Возвращает inspection-снимок layered memory ассистента.
     *
     * @return текущее состояние памяти вместе с активной short-term стратегией.
     */
    fun inspectMemory(): MemorySnapshot

    /**
     * Возвращает дополнительную capability текущего агента, если она поддерживается.
     *
     * @param capabilityType ожидаемый тип capability.
     * @return capability или `null`, если она недоступна.
     */
    fun <TCapability : AgentCapability> capability(capabilityType: Class<TCapability>): TCapability?
}
