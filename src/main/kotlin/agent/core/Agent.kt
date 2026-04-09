package agent.core

import agent.capability.AgentCapability
import agent.format.ResponseFormat
import agent.memory.model.ManagedMemoryNoteEdit
import agent.memory.model.ManagedMemoryNoteResult
import agent.memory.model.MemoryLayer
import agent.memory.model.MemoryNote
import agent.memory.model.MemorySnapshot
import agent.memory.model.PendingMemoryState
import agent.memory.model.PendingMemoryActionResult
import agent.memory.model.PendingMemoryEdit
import agent.memory.model.UserAccount
import agent.task.model.ExpectedAction
import agent.task.model.TaskStage
import agent.task.model.TaskState
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
     * Показывает, будет ли следующий пользовательский prompt требовать реального вызова модели.
     *
     * Используется orchestration-слоем и UI, чтобы не запускать модель там, где ответ можно вернуть
     * детерминированно, например при жёсткой task-aware блокировке.
     *
     * @param userPrompt текст нового пользовательского сообщения.
     * @return `true`, если для ответа нужен вызов модели; иначе `false`.
     */
    fun shouldCallModel(userPrompt: String): Boolean

    /**
     * Возвращает assembled prompt для гипотетического следующего пользовательского сообщения.
     *
     * Используется только в debug- и smoke-сценариях, чтобы показать фактический запрос,
     * который будет отправлен в модель, без выполнения model request.
     */
    fun previewModelPrompt(userPrompt: String): String

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

    fun users(): List<UserAccount>

    fun activeUser(): UserAccount

    fun createUser(userId: String, displayName: String? = null): UserAccount

    fun switchUser(userId: String): UserAccount

    fun inspectProfile(): List<MemoryNote>

    /**
     * Возвращает текущее состояние conversation-scoped задачи, если она уже создана.
     */
    fun inspectTask(): TaskState?

    /**
     * Создаёт новую текущую задачу.
     */
    fun startTask(title: String): TaskState

    /**
     * Обновляет stage текущей задачи.
     */
    fun updateTaskStage(stage: TaskStage): TaskState

    /**
     * Обновляет текущий шаг задачи.
     */
    fun updateTaskStep(step: String): TaskState

    /**
     * Обновляет ожидаемое действие задачи.
     */
    fun updateTaskExpectedAction(action: ExpectedAction): TaskState

    /**
     * Ставит текущую задачу на паузу.
     */
    fun pauseTask(): TaskState

    /**
     * Возобновляет текущую задачу.
     */
    fun resumeTask(): TaskState

    /**
     * Помечает текущую задачу как завершённую.
     */
    fun completeTask(): TaskState

    /**
     * Полностью очищает текущую задачу.
     */
    fun clearTask()

    /**
     * Возвращает текущие pending-кандидаты на сохранение в durable memory.
     */
    fun inspectPendingMemory(): PendingMemoryState

    /**
     * Подтверждает pending-кандидаты по идентификаторам или всю очередь целиком.
     */
    fun approvePendingMemory(candidateIds: List<String> = emptyList()): PendingMemoryActionResult

    /**
     * Отклоняет pending-кандидаты по идентификаторам или всю очередь целиком.
     */
    fun rejectPendingMemory(candidateIds: List<String> = emptyList()): PendingMemoryActionResult

    /**
     * Редактирует pending-кандидат перед подтверждением.
     */
    fun editPendingMemory(candidateId: String, edit: PendingMemoryEdit): PendingMemoryState

    /**
     * Возвращает допустимые категории заметок для выбранного durable memory слоя.
     */
    fun memoryCategories(layer: MemoryLayer): List<String>

    /**
     * Добавляет заметку в выбранный durable memory слой.
     */
    fun addMemoryNote(layer: MemoryLayer, category: String, content: String): ManagedMemoryNoteResult

    /**
     * Редактирует уже сохранённую durable-заметку.
     */
    fun editMemoryNote(layer: MemoryLayer, noteId: String, edit: ManagedMemoryNoteEdit): ManagedMemoryNoteResult

    /**
     * Удаляет заметку из выбранного durable memory слоя.
     */
    fun deleteMemoryNote(layer: MemoryLayer, noteId: String): ManagedMemoryNoteResult

    fun addProfileNote(category: String, content: String): ManagedMemoryNoteResult

    fun editProfileNote(noteId: String, edit: ManagedMemoryNoteEdit): ManagedMemoryNoteResult

    fun deleteProfileNote(noteId: String): ManagedMemoryNoteResult

    /**
     * Возвращает дополнительную capability текущего агента, если она поддерживается.
     *
     * @param capabilityType ожидаемый тип capability.
     * @return capability или `null`, если она недоступна.
     */
    fun <TCapability : AgentCapability> capability(capabilityType: Class<TCapability>): TCapability?
}
