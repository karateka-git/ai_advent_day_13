package agent.impl

import agent.lifecycle.NoOpAgentLifecycleListener
import agent.task.core.DefaultTaskManager
import agent.task.model.ExpectedAction
import agent.task.model.TaskStage
import agent.task.model.TaskState
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
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
            taskManager = DefaultTaskManager(
                initialTask = TaskState(
                    title = "Реализовать task subsystem",
                    stage = TaskStage.EXECUTION,
                    currentStep = "Подключить task prompt",
                    expectedAction = ExpectedAction.AGENT_EXECUTION
                )
            )
        )

        val response = agent.ask("Привет")

        assertEquals("ok", response.content)
        val systemMessage = languageModel.lastMessages.first()
        assertEquals(ChatRole.SYSTEM, systemMessage.role)
        assertTrue(systemMessage.content.contains("Task state"))
        assertTrue(systemMessage.content.contains("Реализовать task subsystem"))
        assertTrue(systemMessage.content.contains("Выполнение"))
        assertTrue(systemMessage.content.contains("agent_execution"))
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
                    title = "Реализовать task subsystem",
                    stage = TaskStage.EXECUTION,
                    currentStep = "Подключить task prompt",
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
}

private class RecordingLanguageModel(
    override val tokenCounter: TokenCounter? = null
) : LanguageModel {
    override val info = LanguageModelInfo(
        name = "RecordingLanguageModel",
        model = "recording-model"
    )

    var lastMessages: List<ChatMessage> = emptyList()

    override fun complete(messages: List<ChatMessage>): LanguageModelResponse {
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
