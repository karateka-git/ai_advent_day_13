import agent.core.AgentTokenStats
import agent.memory.model.MemoryLayer
import agent.memory.model.MemorySnapshot
import agent.memory.model.MemoryState
import agent.memory.model.PendingMemoryCandidate
import agent.memory.model.PendingMemoryState
import agent.memory.model.ShortTermMemory
import agent.memory.strategy.MemoryStrategyType
import agent.task.model.ExpectedAction
import agent.task.model.TaskItem
import agent.task.model.TaskStage
import agent.task.model.TaskState
import agent.task.model.TaskStatus
import app.output.AppEvent
import app.output.HelpCommandDescriptor
import app.output.HelpCommandGroup
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import llm.core.model.ChatMessage
import llm.core.model.ChatRole
import llm.core.model.TokenUsage
import ui.cli.CliRenderer

class CliRendererTest {
    @Test
    fun `session start prints bordered help hint only`() {
        val output = captureStdout {
            CliRenderer().emit(AppEvent.SessionStarted)
        }

        assertTrue(output.contains("Старт"))
        assertTrue(output.contains("/help"))
        assertFalse(output.contains("/models"))
        assertFalse(output.contains("/use <id>"))
    }

    @Test
    fun `general help prints bordered commands block`() {
        val output = captureStdout {
            CliRenderer().emit(
                AppEvent.CommandsAvailable(
                    title = "Commands",
                    groups = listOf(
                        HelpCommandGroup(
                            title = "General",
                            commands = listOf(
                                HelpCommandDescriptor("/help", "Show commands."),
                                HelpCommandDescriptor("/memory", "Show memory.")
                            )
                        )
                    )
                )
            )
        }

        assertTrue(output.contains("Commands"))
        assertTrue(output.contains("General"))
        assertTrue(output.contains("/help"))
        assertTrue(output.contains("Show commands."))
        assertTrue(output.contains("/memory"))
    }

    @Test
    fun `auto pending notification is rendered as command result`() {
        val output = captureStdout {
            CliRenderer().emit(
                AppEvent.PendingMemoryAvailable(
                    pending = samplePending(),
                    reason = "Есть кандидаты на сохранение в память. Посмотри их командой /memory pending."
                )
            )
        }

        assertTrue(output.contains("Результат команды"))
        assertTrue(output.contains("/memory pending"))
        assertFalse(output.contains("p1 ["))
    }

    @Test
    fun `manual pending view prints bordered block with help hint`() {
        val output = captureStdout {
            CliRenderer().emit(
                AppEvent.PendingMemoryAvailable(
                    pending = samplePending()
                )
            )
        }

        assertTrue(output.contains("Pending-память"))
        assertTrue(output.contains("p1"))
        assertTrue(output.contains("2 weeks"))
        assertTrue(output.contains("/memory pending info"))
    }

    @Test
    fun `task view prints bordered block`() {
        val output = captureStdout {
            CliRenderer().emit(
                AppEvent.TaskStateAvailable(
                    TaskState(
                        title = "Реализовать task subsystem",
                        stage = TaskStage.VALIDATION,
                        currentStep = "Проверить CLI-команды",
                        expectedAction = ExpectedAction.USER_CONFIRMATION,
                        status = TaskStatus.PAUSED
                    )
                )
            )
        }

        assertTrue(output.contains("Задача"))
        assertTrue(output.contains("Реализовать task subsystem"))
        assertTrue(output.contains("Проверка"))
        assertTrue(output.contains("на паузе"))
        assertTrue(output.contains("ожидается подтверждение пользователя"))
    }

    @Test
    fun `task list prints active marker and task summary`() {
        val output = captureStdout {
            CliRenderer().emit(
                AppEvent.TaskListAvailable(
                    tasks = listOf(
                        TaskItem(
                            id = "task-1",
                            title = "Реализовать task subsystem",
                            stage = TaskStage.EXECUTION,
                            currentStep = "Подключить CLI",
                            expectedAction = ExpectedAction.AGENT_EXECUTION,
                            status = TaskStatus.PAUSED
                        ),
                        TaskItem(
                            id = "task-2",
                            title = "Подготовить smoke-check",
                            stage = TaskStage.VALIDATION,
                            currentStep = "Прогнать сценарий",
                            expectedAction = ExpectedAction.USER_CONFIRMATION,
                            status = TaskStatus.ACTIVE
                        )
                    ),
                    activeTaskId = "task-2"
                )
            )
        }

        assertTrue(output.contains("Задачи"))
        assertTrue(output.contains("Активная задача: task-2"))
        assertTrue(output.contains("* task-2 | Подготовить smoke-check"))
        assertTrue(output.contains("task-1 | Реализовать task subsystem"))
        assertTrue(output.contains("Активна"))
        assertTrue(output.contains("Проверка"))
    }

    @Test
    fun `memory view shows derived short term only`() {
        val output = captureStdout {
            CliRenderer().emit(
                AppEvent.MemoryStateAvailable(
                    snapshot = MemorySnapshot(
                        state = MemoryState(
                            shortTerm = ShortTermMemory(
                                rawMessages = listOf(
                                    ChatMessage(
                                        role = ChatRole.SYSTEM,
                                        content = "Raw line 1\nRaw line 2"
                                    )
                                ),
                                derivedMessages = listOf(
                                    ChatMessage(role = ChatRole.SYSTEM, content = "Derived system"),
                                    ChatMessage(role = ChatRole.USER, content = "Derived user")
                                )
                            )
                        ),
                        shortTermStrategyType = MemoryStrategyType.NO_COMPRESSION
                    ),
                    selectedLayer = null
                )
            )
        }

        assertTrue(output.contains("Память"))
        assertTrue(output.contains("Используемая стратегия: no_compression"))
        assertFalse(output.contains("Raw line 1"))
        assertFalse(output.contains("Derived system"))
        assertTrue(output.contains("пользователь: Derived user"))
    }

    @Test
    fun `user prompt is bordered`() {
        val output = captureStdout {
            CliRenderer().emit(AppEvent.UserInputPrompt(ChatRole.USER))
        }

        assertTrue(output.contains("Пользователь"))
    }

    @Test
    fun `assistant response is bordered`() {
        val output = captureStdout {
            CliRenderer().emit(
                AppEvent.AssistantResponseAvailable(
                    role = ChatRole.ASSISTANT,
                    content = "Короткий ответ",
                    tokenStats = AgentTokenStats(
                        apiUsage = TokenUsage(
                            promptTokens = 10,
                            completionTokens = 5,
                            totalTokens = 15
                        )
                    )
                )
            )
        }

        assertTrue(output.contains("Ассистент"))
        assertTrue(output.contains("Короткий ответ"))
        assertTrue(output.contains("Токены ответа:"))
    }

    @Test
    fun `model prompt is rendered as bordered debug block`() {
        val output = captureStdout {
            CliRenderer().emit(
                AppEvent.ModelPromptAvailable(
                    "Система:\nБазовый промпт\n\nПользователь:\nОбычное сообщение"
                )
            )
        }

        assertTrue(output.contains("Запрос к модели"))
        assertTrue(output.contains("Базовый промпт"))
        assertTrue(output.contains("Обычное сообщение"))
    }

    private fun samplePending(): PendingMemoryState =
        PendingMemoryState(
            candidates = listOf(
                PendingMemoryCandidate(
                    id = "p1",
                    targetLayer = MemoryLayer.WORKING,
                    category = "deadline",
                    content = "2 weeks",
                    sourceRole = ChatRole.USER,
                    sourceMessage = "Deadline is 2 weeks"
                )
            ),
            nextId = 2
        )

    private fun captureStdout(block: () -> Unit): String {
        val originalOut = System.out
        val buffer = ByteArrayOutputStream()
        System.setOut(PrintStream(buffer, true, Charsets.UTF_8))
        return try {
            block()
            buffer.toString(Charsets.UTF_8)
        } finally {
            System.setOut(originalOut)
        }
    }
}
