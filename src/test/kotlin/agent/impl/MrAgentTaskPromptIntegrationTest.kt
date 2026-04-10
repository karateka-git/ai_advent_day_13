package agent.impl

import agent.lifecycle.NoOpAgentLifecycleListener
import agent.memory.strategy.nocompression.NoCompressionMemoryStrategy
import agent.task.core.DefaultTaskManager
import agent.task.core.DefaultTaskOrchestrationService
import agent.task.model.ExpectedAction
import agent.task.model.TaskStage
import agent.task.model.TaskState
import agent.task.model.TaskStages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import llm.core.LanguageModel
import llm.core.model.ChatMessage
import llm.core.model.ChatRole
import llm.core.model.LanguageModelInfo
import llm.core.model.LanguageModelResponse
import llm.core.model.TokenUsage
import llm.core.tokenizer.TokenCounter

class MrAgentTaskPromptIntegrationTest {
    @Test
    fun `injects task block into system prompt before model request`() {
        val languageModel = RecordingLanguageModel()
        val agent = MrAgent(
            languageModel = languageModel,
            lifecycleListener = NoOpAgentLifecycleListener,
            memoryStrategy = NoCompressionMemoryStrategy(),
            taskManager = DefaultTaskManager(
                initialTask = TaskState(
                    title = "Implement task subsystem",
                    stage = TaskStage.EXECUTION,
                    currentStep = "Connect task prompt",
                    expectedAction = ExpectedAction.AGENT_EXECUTION
                )
            )
        )

        val response = agent.ask("Привет")

        assertEquals("ok", response.content)
        val allMessages = languageModel.lastMessages.joinToString(separator = "\n---\n") { message ->
            "${message.role}: ${message.content}"
        }
        val systemMessage = languageModel.lastMessages.first()
        assertEquals(ChatRole.SYSTEM, systemMessage.role)
        assertTrue(systemMessage.content.contains("Состояние задачи"), allMessages)
        assertTrue(systemMessage.content.contains("Implement task subsystem"), allMessages)
        assertTrue(systemMessage.content.contains("- Этап: ${TaskStages.definitionFor(TaskStage.EXECUTION).label}"), allMessages)
        assertTrue(systemMessage.content.contains("- Ожидаемое действие: выполнение агентом"), allMessages)
        assertTrue(systemMessage.content.contains("- Текущий шаг: Connect task prompt"), allMessages)
        assertTrue(systemMessage.content.contains("Правила интерпретации task state"), allMessages)
        assertTrue(systemMessage.content.contains("Приоритет: статус -> ожидаемое действие -> этап -> история."), allMessages)
        assertTrue(systemMessage.content.contains("Поведение по задаче"), allMessages)
    }

    @Test
    fun `preview token stats include task block`() {
        val languageModel = RecordingLanguageModel(
            tokenCounter = CharacterTokenCounter()
        )
        val agent = MrAgent(
            languageModel = languageModel,
            lifecycleListener = NoOpAgentLifecycleListener,
            taskManager = DefaultTaskManager(
                initialTask = TaskState(
                    title = "Implement task subsystem",
                    stage = TaskStage.EXECUTION,
                    currentStep = "Connect task prompt",
                    expectedAction = ExpectedAction.AGENT_EXECUTION
                )
            )
        )

        val stats = agent.previewTokenStats("Привет")

        assertNotNull(stats.historyTokens)
        assertNotNull(stats.promptTokensLocal)
        assertTrue(stats.promptTokensLocal!! >= stats.historyTokens!!)
        assertTrue(stats.promptTokensLocal!! > 0)
    }

    @Test
    fun `paused task skips model request and returns deterministic response`() {
        val languageModel = RecordingLanguageModel()
        val agent = MrAgent(
            languageModel = languageModel,
            lifecycleListener = NoOpAgentLifecycleListener,
            taskManager = DefaultTaskManager(
                initialTask = TaskState(
                    title = "Implement task subsystem",
                    stage = TaskStage.EXECUTION,
                    currentStep = "Wait",
                    expectedAction = ExpectedAction.AGENT_EXECUTION,
                    status = agent.task.model.TaskStatus.PAUSED
                )
            ),
            taskOrchestrationService = DefaultTaskOrchestrationService()
        )

        val response = agent.ask("Продолжай")

        assertTrue(response.content.contains("на паузе"))
        assertFalse(agent.shouldCallModel("Продолжай"))
    }

    @Test
    fun `preview task behavior explains guided mode`() {
        val languageModel = RecordingLanguageModel()
        val agent = MrAgent(
            languageModel = languageModel,
            lifecycleListener = NoOpAgentLifecycleListener,
            taskManager = DefaultTaskManager(
                initialTask = TaskState(
                    title = "Implement task subsystem",
                    stage = TaskStage.PLANNING,
                    currentStep = "Choose storage format",
                    expectedAction = ExpectedAction.USER_INPUT
                )
            ),
            taskOrchestrationService = DefaultTaskOrchestrationService()
        )

        val notice = agent.previewTaskBehavior("Что дальше?")

        assertNotNull(notice)
        assertTrue(notice.contains("Контекст задачи 'Implement task subsystem'"))
        assertTrue(notice.contains("этап — планирование"))
        assertTrue(notice.contains("ждёт твоего решения"))
    }

    @Test
    fun `prompt uses only active task when previous task is paused`() {
        val languageModel = RecordingLanguageModel()
        val taskManager = DefaultTaskManager().apply {
            startTask("Первая задача")
            updateStep("Старый шаг")
            pauseTask()
            startTask("Вторая задача")
            updateStage(TaskStage.VALIDATION)
            updateStep("Проверить новый результат")
            updateExpectedAction(ExpectedAction.USER_CONFIRMATION)
        }
        val agent = MrAgent(
            languageModel = languageModel,
            lifecycleListener = NoOpAgentLifecycleListener,
            memoryStrategy = NoCompressionMemoryStrategy(),
            taskManager = taskManager
        )

        agent.ask("Проверь текущую задачу")

        val systemMessage = languageModel.lastMessages.first()
        assertTrue(systemMessage.content.contains("Вторая задача"))
        assertTrue(systemMessage.content.contains("Проверить новый результат"))
        assertFalse(systemMessage.content.contains("Первая задача"))
        assertFalse(systemMessage.content.contains("Старый шаг"))
    }
}

private class RecordingLanguageModel(
    override val tokenCounter: TokenCounter? = null
) : LanguageModel {
    override val info = LanguageModelInfo(
        name = "RecordingLanguageModel",
        model = "recording-model"
    )

    var lastMessages: List<ChatMessage> = emptyList()
    var completeCalls: Int = 0

    override fun complete(messages: List<ChatMessage>): LanguageModelResponse {
        completeCalls += 1
        lastMessages = messages
        return LanguageModelResponse(
            content = "ok",
            usage = TokenUsage(
                promptTokens = 10,
                completionTokens = 2,
                totalTokens = 12
            )
        )
    }
}

private class CharacterTokenCounter : TokenCounter {
    override fun countText(text: String): Int = text.length
}
