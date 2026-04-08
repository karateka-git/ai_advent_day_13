package agent.impl

import agent.lifecycle.NoOpAgentLifecycleListener
import agent.task.core.DefaultTaskManager
import agent.task.model.ExpectedAction
import agent.task.model.TaskStage
import agent.task.model.TaskState
import agent.task.model.TaskStages
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertTrue(systemMessage.content.contains("Task state"), allMessages)
        assertTrue(systemMessage.content.contains("Implement task subsystem"), allMessages)
        assertTrue(systemMessage.content.contains("- Stage: ${TaskStages.definitionFor(TaskStage.EXECUTION).label}"), allMessages)
        assertTrue(systemMessage.content.contains("- Expected action: agent_execution"), allMessages)
        assertTrue(systemMessage.content.contains("- Current step: Connect task prompt"), allMessages)
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
