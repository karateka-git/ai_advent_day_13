package app.output

import agent.core.AgentTokenStats
import agent.task.model.ExpectedAction
import agent.task.model.TaskStage
import agent.task.model.TaskState
import agent.task.model.TaskStatus
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import llm.core.model.ChatRole

class DebugTraceListenerTest {
    @Test
    fun `writes structured records for user input and model prompt`() {
        val tempDir = createTempDirectory()
        val traceFile = tempDir.resolve("trace.jsonl")
        val listener = DebugTraceListener(traceFile)

        listener.emit(AppEvent.UserInputReceived(ChatRole.USER, "Обычное сообщение"))
        listener.emit(AppEvent.TokenPreviewAvailable(AgentTokenStats(userPromptTokens = 14, historyTokens = 120, promptTokensLocal = 136)))
        listener.emit(AppEvent.ModelPromptAvailable("System:\nBase prompt"))

        val records = Files.readAllLines(traceFile).filter(String::isNotBlank).map {
            Json.decodeFromString<DebugTraceRecord>(it)
        }

        assertEquals(
            listOf(
                DebugTraceRecord(
                    kind = "user_input",
                    title = "Пользователь",
                    lines = listOf("Обычное сообщение")
                ),
                DebugTraceRecord(
                    kind = "token_preview",
                    title = "Оценка перед запросом",
                    lines = listOf(
                        "  текущее сообщение: 14",
                        "  история диалога: 120",
                        "  полный запрос: 136"
                    )
                ),
                DebugTraceRecord(
                    kind = "model_prompt",
                    title = "Запрос к модели",
                    lines = listOf("System:", "Base prompt")
                )
            ),
            records
        )
    }

    @Test
    fun `writes task state as structured trace block`() {
        val tempDir = createTempDirectory()
        val traceFile = tempDir.resolve("task-trace.jsonl")
        val listener = DebugTraceListener(traceFile)

        listener.emit(
            AppEvent.TaskStateAvailable(
                TaskState(
                    title = "Реализовать task subsystem",
                    stage = TaskStage.COMPLETION,
                    currentStep = "Собрать итог",
                    expectedAction = ExpectedAction.USER_CONFIRMATION,
                    status = TaskStatus.DONE
                )
            )
        )

        val record = Json.decodeFromString<DebugTraceRecord>(Files.readAllLines(traceFile).single())

        assertEquals("task_state", record.kind)
        assertEquals("Задача", record.title)
        assertEquals(
            listOf(
                "Название: Реализовать task subsystem",
                "Этап: Завершение",
                "Статус: завершена",
                "Ожидаемое действие: ожидается подтверждение пользователя",
                "Описание этапа: Оформление итога и закрытие задачи",
                "Текущий шаг: Собрать итог"
            ),
            record.lines
        )
    }
}
