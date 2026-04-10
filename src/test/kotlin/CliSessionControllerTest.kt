import agent.capability.AgentCapability
import agent.core.Agent
import agent.core.AgentInfo
import agent.core.AgentResponse
import agent.core.AgentTokenStats
import agent.format.ResponseFormat
import agent.format.TextResponseFormat
import agent.lifecycle.AgentLifecycleListener
import agent.lifecycle.NoOpAgentLifecycleListener
import agent.memory.model.ManagedMemoryNoteEdit
import agent.memory.model.ManagedMemoryNoteResult
import agent.memory.model.MemoryLayer
import agent.memory.model.MemoryNote
import agent.memory.model.MemorySnapshot
import agent.memory.model.MemoryState
import agent.memory.model.PendingMemoryActionResult
import agent.memory.model.PendingMemoryCandidate
import agent.memory.model.PendingMemoryEdit
import agent.memory.model.PendingMemoryState
import agent.memory.model.UserAccount
import agent.memory.strategy.MemoryStrategyOption
import agent.memory.strategy.MemoryStrategyType
import agent.task.model.ExpectedAction
import agent.task.model.TaskItem
import agent.task.model.TaskStage
import agent.task.model.TaskState
import agent.task.model.TaskStatus
import app.output.AppEvent
import app.output.AppEventSink
import java.net.http.HttpClient
import java.nio.file.Path
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import llm.core.LanguageModel
import llm.core.model.ChatMessage
import llm.core.model.ChatRole
import llm.core.model.LanguageModelInfo
import llm.core.model.LanguageModelResponse
import ui.cli.CliSessionController
import ui.cli.CliSessionControllerResult
import ui.cli.CliSessionState
import ui.cli.GeneralCliCatalog
import ui.cli.PendingMemoryCliCatalog

class CliSessionControllerTest {
    private val config = Properties()
    private val httpClient = HttpClient.newHttpClient()
    private val lifecycleListener: AgentLifecycleListener = NoOpAgentLifecycleListener
    private val strategyOption = MemoryStrategyOption(
        type = MemoryStrategyType.NO_COMPRESSION,
        displayName = "Без сжатия",
        description = "Тестовая стратегия."
    )

    @Test
    fun `shows general help`() {
        val sink = RecordingAppEventSink()
        val controller = createController(sink = sink)

        val result = controller.handle("/help")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(
            listOf<AppEvent>(
                AppEvent.CommandsAvailable(
                    title = "Доступные команды",
                    groups = GeneralCliCatalog.helpGroups
                )
            ),
            sink.events
        )
    }

    @Test
    fun `shows users and active profile`() {
        val sink = RecordingAppEventSink()
        val controller = createController(sink = sink)

        val result = controller.handle("/users")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(
            listOf<AppEvent>(
                AppEvent.UsersAvailable(
                    users = listOf(UserAccount("default", "Default")),
                    activeUserId = "default"
                )
            ),
            sink.events
        )
    }

    @Test
    fun `shows active user profile`() {
        val sink = RecordingAppEventSink()
        val controller = createController(sink = sink)

        val result = controller.handle("/profile")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(
            listOf<AppEvent>(
                AppEvent.UserProfileAvailable(
                    user = UserAccount("default", "Default"),
                    notes = listOf(
                        MemoryNote(
                            id = "n-profile",
                            category = "communication_style",
                            content = "Отвечай кратко"
                        )
                    )
                )
            ),
            sink.events
        )
    }

    @Test
    fun `shows current task`() {
        val sink = RecordingAppEventSink()
        val controller = createController(sink = sink)

        val result = controller.handle("/task")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(
            listOf<AppEvent>(
                AppEvent.TaskStateAvailable(
                    TaskState(
                        title = "Реализовать task subsystem",
                        stage = TaskStage.EXECUTION,
                        currentStep = "Подключить CLI",
                        expectedAction = ExpectedAction.AGENT_EXECUTION,
                        status = TaskStatus.ACTIVE
                    )
                )
            ),
            sink.events
        )
    }

    @Test
    fun `shows task list with active marker`() {
        val sink = RecordingAppEventSink()
        val controller = createController(sink = sink)

        val result = controller.handle("/task list")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(
            listOf<AppEvent>(
                AppEvent.TaskListAvailable(
                    tasks = listOf(
                        TaskItem(
                            id = "task-1",
                            title = "Реализовать task subsystem",
                            stage = TaskStage.EXECUTION,
                            currentStep = "Подключить CLI",
                            expectedAction = ExpectedAction.AGENT_EXECUTION,
                            status = TaskStatus.ACTIVE
                        ),
                        TaskItem(
                            id = "task-2",
                            title = "Подготовить smoke-check",
                            stage = TaskStage.PLANNING,
                            currentStep = "Собрать сценарий",
                            expectedAction = ExpectedAction.USER_INPUT,
                            status = TaskStatus.PAUSED
                        )
                    ),
                    activeTaskId = "task-1"
                )
            ),
            sink.events
        )
    }

    @Test
    fun `updates task through current agent`() {
        val sink = RecordingAppEventSink()
        val agent = FakeAgent()
        val controller = createController(
            sink = sink,
            initialState = initialState(agent = agent)
        )

        val result = controller.handle("/task pause")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(TaskStatus.PAUSED, agent.taskState?.status)
        assertEquals(
            listOf<AppEvent>(
                AppEvent.CommandCompleted("Задача 'Реализовать task subsystem' поставлена на паузу.")
            ),
            sink.events
        )
    }

    @Test
    fun `switches task by id through current agent`() {
        val sink = RecordingAppEventSink()
        val agent = FakeAgent()
        val controller = createController(
            sink = sink,
            initialState = initialState(agent = agent)
        )

        val result = controller.handle("/task switch task-2")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals("task-2", agent.activeTask()?.id)
        assertEquals(
            listOf<AppEvent>(
                AppEvent.CommandCompleted("Задача 'Подготовить smoke-check' теперь активна.")
            ),
            sink.events
        )
    }

    @Test
    fun `resumes task by id through current agent`() {
        val sink = RecordingAppEventSink()
        val agent = FakeAgent().apply {
            taskState = requireTaskState().copy(status = TaskStatus.PAUSED)
        }
        val controller = createController(
            sink = sink,
            initialState = initialState(agent = agent)
        )

        val result = controller.handle("/task resume task-2")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(TaskStatus.ACTIVE, agent.activeTask()?.status)
        assertEquals(
            listOf<AppEvent>(
                AppEvent.CommandCompleted("Задача 'Подготовить smoke-check' возобновлена.")
            ),
            sink.events
        )
    }

    @Test
    fun `completes task by id through current agent`() {
        val sink = RecordingAppEventSink()
        val agent = FakeAgent()
        val controller = createController(
            sink = sink,
            initialState = initialState(agent = agent)
        )

        val result = controller.handle("/task done task-2")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(TaskStatus.DONE, agent.listTasks().first { it.id == "task-2" }.status)
        assertEquals(
            listOf<AppEvent>(
                AppEvent.CommandCompleted("Задача 'Подготовить smoke-check' отмечена как завершённая.")
            ),
            sink.events
        )
    }

    @Test
    fun `starting task reports that previous active task was paused`() {
        val sink = RecordingAppEventSink()
        val agent = FakeAgent()
        val controller = createController(
            sink = sink,
            initialState = initialState(agent = agent)
        )

        val result = controller.handle("/task start Вторая задача")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals("Вторая задача", agent.taskState?.title)
        assertEquals(
            listOf<AppEvent>(
                AppEvent.CommandCompleted(
                    "Создана задача 'Вторая задача' на этапе Планирование. Предыдущая активная задача 'Реализовать task subsystem' сохранена и переведена в паузу."
                )
            ),
            sink.events
        )
    }

    @Test
    fun `regular prompt does not mutate paused task state`() {
        val sink = RecordingAppEventSink()
        val agent = FakeAgent().apply {
            taskState = requireTaskState().copy(status = TaskStatus.PAUSED)
            shouldCallModel = false
            response = AgentResponse(
                content = "Текущая задача 'Реализовать task subsystem' стоит на паузе. Возобнови её через /task resume, если хочешь продолжить этот рабочий трек.",
                tokenStats = AgentTokenStats(userPromptTokens = 1)
            )
        }
        val expectedTaskState = requireNotNull(taskStateOrNull(agent))
        val controller = createController(
            sink = sink,
            initialState = initialState(agent = agent)
        )

        val result = controller.handle("Обычное сообщение без /task во время paused-задачи")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(expectedTaskState, agent.taskState)
        assertTrue(sink.events.none { it == AppEvent.ModelRequestStarted })
    }

    @Test
    fun `switching active user does not mutate current task state`() {
        val sink = RecordingAppEventSink()
        val agent = FakeAgent()
        val expectedTaskState = requireNotNull(taskStateOrNull(agent))
        val controller = createController(
            sink = sink,
            initialState = initialState(agent = agent)
        )

        val result = controller.handle("/user use reviewer")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(expectedTaskState, agent.taskState)
        assertEquals(
            listOf<AppEvent>(
                AppEvent.CommandCompleted("Активный пользователь переключён на reviewer (reviewer).")
            ),
            sink.events
        )
    }

    @Test
    fun `adds memory note through current agent`() {
        val sink = RecordingAppEventSink()
        val controller = createController(sink = sink)

        val result = controller.handle("/memory add working goal Собрать ТЗ")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(
            listOf<AppEvent>(
                AppEvent.CommandCompleted("Добавлена заметка n1 в слой рабочая память.")
            ),
            sink.events
        )
    }

    @Test
    fun `starts step by step memory note creation`() {
        val sink = RecordingAppEventSink()
        val agent = FakeAgent()
        val controller = createController(
            sink = sink,
            initialState = initialState(agent = agent)
        )

        controller.handle("/memory add")
        controller.handle("working")
        controller.handle("goal")
        controller.handle("Собрать ТЗ")
        val result = controller.handle("confirm")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(
            listOf(
                Triple(MemoryLayer.WORKING, "goal", "Собрать ТЗ")
            ),
            agent.addedMemoryNotes
        )
        assertEquals(
            listOf<AppEvent>(
                AppEvent.CommandCompleted("Пошаговое добавление заметки запущено. Выбери слой: working или long. Для отмены введи /cancel."),
                AppEvent.CommandCompleted(
                    "Слой выбран: working.\n" +
                        "Введи категорию из списка:\n" +
                        "- goal: цель или ожидаемый результат текущей задачи\n" +
                        "- constraint: ограничение, требование или запрет для текущей задачи\n" +
                        "- deadline: срок, дата или временное ограничение текущей задачи\n" +
                        "- budget: бюджет или лимит ресурсов\n" +
                        "- integration: внешняя система, сервис или интеграция, нужная для задачи\n" +
                        "- decision: текущее принятое решение по задаче\n" +
                        "- open_question: открытый вопрос или нерешённая часть текущей задачи"
                ),
                AppEvent.CommandCompleted("Категория выбрана: goal. Теперь введи текст заметки."),
                AppEvent.CommandCompleted("Черновик заметки готов.\nСлой: working\nКатегория: goal\nТекст: Собрать ТЗ\nВведи confirm для сохранения или /cancel для отмены."),
                AppEvent.CommandCompleted("Добавлена заметка n1 в слой рабочая память.")
            ),
            sink.events
        )
    }

    @Test
    fun `starts step by step profile note creation`() {
        val sink = RecordingAppEventSink()
        val agent = FakeAgent()
        val controller = createController(
            sink = sink,
            initialState = initialState(agent = agent)
        )

        controller.handle("/profile add")
        controller.handle("communication_style")
        controller.handle("Отвечай кратко")
        val result = controller.handle("confirm")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(
            listOf(
                "communication_style" to "Отвечай кратко"
            ),
            agent.addedProfileNotes
        )
        assertEquals(
            listOf<AppEvent>(
                AppEvent.CommandCompleted(
                    "Пошаговое добавление профильной заметки запущено для пользователя Default.\n" +
                        "Введи категорию из списка:\n" +
                        "- communication_style: устойчивое предпочтение по стилю общения\n" +
                        "- persistent_preference: постоянное пользовательское предпочтение\n" +
                        "- architectural_agreement: устойчивая архитектурная договорённость\n" +
                        "- reusable_knowledge: повторно полезное знание о пользователе или проекте\n" +
                        "Для отмены введи /cancel."
                ),
                AppEvent.CommandCompleted("Категория выбрана: communication_style. Теперь введи текст профильной заметки."),
                AppEvent.CommandCompleted("Черновик профильной заметки готов.\nПользователь: Default (default)\nКатегория: communication_style\nТекст: Отвечай кратко\nВведи confirm для сохранения или /cancel для отмены."),
                AppEvent.CommandCompleted("Профильная заметка n-profile добавлена.")
            ),
            sink.events
        )
    }

    @Test
    fun `handles regular prompt and emits pending candidates`() {
        val sink = RecordingAppEventSink()
        val agent = FakeAgent(
            previewTokenStats = AgentTokenStats(historyTokens = 10),
            response = AgentResponse(
                content = "Ответ",
                tokenStats = AgentTokenStats(promptTokensLocal = 15)
            ),
            initialPendingState = PendingMemoryState(),
            pendingStateAfterAsk = FakeAgent.pendingMemory()
        )
        val controller = createController(
            sink = sink,
            initialState = initialState(agent = agent)
        )

        val result = controller.handle("Расскажи историю")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(
            listOf<AppEvent>(
                AppEvent.TokenPreviewAvailable(AgentTokenStats(historyTokens = 10)),
                AppEvent.ModelRequestStarted,
                AppEvent.ModelRequestFinished,
                AppEvent.AssistantResponseAvailable(
                    role = ChatRole.ASSISTANT,
                    content = "Ответ",
                    tokenStats = AgentTokenStats(promptTokensLocal = 15)
                ),
                AppEvent.PendingMemoryAvailable(
                    pending = FakeAgent.pendingMemory(),
                    reason = "Есть кандидаты на сохранение в память. Посмотри их командой /memory pending."
                )
            ),
            sink.events
        )
    }

    @Test
    fun `emits model prompt for regular prompt when debug flag is enabled`() {
        val sink = RecordingAppEventSink()
        val agent = FakeAgent(
            previewTokenStats = AgentTokenStats(historyTokens = 10),
            previewTaskBehavior = "Контекст задачи: отвечу в режиме планирования.",
            previewModelPrompt = "Система:\nБазовый промпт\n\nПользователь:\nОбычное сообщение"
        )
        val controller = createController(
            sink = sink,
            initialState = initialState(agent = agent),
            showModelPrompt = true
        )

        val result = controller.handle("Обычное сообщение")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(AppEvent.ModelPromptAvailable("Система:\nБазовый промпт\n\nПользователь:\nОбычное сообщение"), sink.events[1])
    }

    @Test
    fun `does not emit model prompt or model request for blocked prompt`() {
        val sink = RecordingAppEventSink()
        val agent = FakeAgent(
            previewTokenStats = AgentTokenStats(userPromptTokens = 5),
            previewModelPrompt = "Запрос к модели пропущен"
        ).apply {
            shouldCallModel = false
            response = AgentResponse(
                content = "Текущая задача 'Реализовать task subsystem' стоит на паузе. Возобнови её через /task resume, если хочешь продолжить этот рабочий трек.",
                tokenStats = AgentTokenStats(userPromptTokens = 5)
            )
        }
        val controller = createController(
            sink = sink,
            initialState = initialState(agent = agent),
            showModelPrompt = true
        )

        val result = controller.handle("Продолжай")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertTrue(sink.events.none { it == AppEvent.ModelRequestStarted })
        assertTrue(sink.events.none { it is AppEvent.ModelPromptAvailable })
    }

    @Test
    fun `emits task behavior notice for guided prompt`() {
        val sink = RecordingAppEventSink()
        val agent = FakeAgent(
            previewTokenStats = AgentTokenStats(historyTokens = 8),
            previewTaskBehavior = "Контекст задачи 'Реализовать task subsystem': этап — планирование. Отвечу в режиме планирования."
        )
        val controller = createController(
            sink = sink,
            initialState = initialState(agent = agent)
        )

        val result = controller.handle("Давай уже писать код")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(
            AppEvent.TaskBehaviorNotice("Контекст задачи 'Реализовать task subsystem': этап — планирование. Отвечу в режиме планирования."),
            sink.events[1]
        )
    }

    private fun createController(
        sink: RecordingAppEventSink = RecordingAppEventSink(),
        initialState: CliSessionState = initialState(),
        showModelPrompt: Boolean = false,
        createLanguageModel: (String, Properties, HttpClient) -> LanguageModel = { _, _, _ ->
            throw AssertionError("Не должен вызываться в этом тесте.")
        },
        createAgent: (LanguageModel, AgentLifecycleListener, MemoryStrategyType) -> Agent<String> = { _, _, _ ->
            throw AssertionError("Не должен вызываться в этом тесте.")
        }
    ): CliSessionController =
        CliSessionController(
            initialState = initialState,
            config = config,
            httpClient = httpClient,
            lifecycleListener = lifecycleListener,
            appEventSink = sink,
            showModelPrompt = showModelPrompt,
            createLanguageModel = createLanguageModel,
            availableModelsProvider = { emptyList() },
            createAgent = createAgent,
            selectMemoryStrategy = { strategyOption },
            warmUpLanguageModel = { _, _ -> Unit }
        )

    private fun initialState(
        modelId: String = "timeweb",
        languageModel: LanguageModel = FakeLanguageModel("initial-model"),
        agent: Agent<String> = FakeAgent()
    ): CliSessionState =
        CliSessionState(
            modelId = modelId,
            languageModel = languageModel,
            agent = agent,
            memoryStrategyOption = strategyOption
        )

    private fun taskStateOrNull(agent: FakeAgent): TaskState? = agent.taskState
}

private class RecordingAppEventSink : AppEventSink {
    val events = mutableListOf<AppEvent>()

    override fun emit(event: AppEvent) {
        events += event
    }
}

private class FakeAgent(
    private val previewTokenStats: AgentTokenStats = AgentTokenStats(),
    private val previewTaskBehavior: String? = null,
    private val previewModelPrompt: String = "Система:\nБазовый промпт",
    var response: AgentResponse<String> = AgentResponse(
        content = "ok",
        tokenStats = AgentTokenStats()
    ),
    initialPendingState: PendingMemoryState = pendingMemory(),
    private val pendingStateAfterAsk: PendingMemoryState = initialPendingState
) : Agent<String> {
    private var currentPendingState: PendingMemoryState = initialPendingState
    val addedMemoryNotes = mutableListOf<Triple<MemoryLayer, String, String>>()
    val addedProfileNotes = mutableListOf<Pair<String, String>>()
    var taskState: TaskState? = TaskState(
        title = "Реализовать task subsystem",
        stage = TaskStage.EXECUTION,
        currentStep = "Подключить CLI",
        expectedAction = ExpectedAction.AGENT_EXECUTION,
        status = TaskStatus.ACTIVE
    )
    var shouldCallModel: Boolean = true

    override val info: AgentInfo = AgentInfo(
        name = "TestAgent",
        description = "Тестовый агент",
        model = "test-model"
    )
    override val responseFormat: ResponseFormat<String> = TextResponseFormat

    override fun previewTokenStats(userPrompt: String): AgentTokenStats = previewTokenStats

    override fun shouldCallModel(userPrompt: String): Boolean = shouldCallModel

    override fun previewTaskBehavior(userPrompt: String): String? = previewTaskBehavior

    override fun previewModelPrompt(userPrompt: String): String = previewModelPrompt

    override fun ask(userPrompt: String): AgentResponse<String> {
        currentPendingState = pendingStateAfterAsk
        return response
    }

    override fun clearContext() = Unit

    override fun replaceContextFromFile(sourcePath: Path) = Unit

    override fun inspectMemory(): MemorySnapshot = memorySnapshot()

    override fun users(): List<UserAccount> = listOf(UserAccount("default", "Default"))

    override fun activeUser(): UserAccount = UserAccount("default", "Default")

    override fun createUser(userId: String, displayName: String?): UserAccount =
        UserAccount(userId, displayName ?: userId)

    override fun switchUser(userId: String): UserAccount =
        UserAccount(userId, userId)

    override fun inspectProfile(): List<MemoryNote> =
        listOf(MemoryNote(id = "n-profile", category = "communication_style", content = "Отвечай кратко"))

    override fun inspectTask(): TaskState? = taskState

    override fun listTasks(): List<TaskItem> = taskItems.toList()

    override fun activeTask(): TaskItem? = activeTaskId?.let { id -> taskItems.firstOrNull { it.id == id } }

    override fun startTask(title: String): TaskState =
        TaskState(title = title).also {
            val newItem = TaskItem(
                id = "task-${taskItems.size + 1}",
                title = title,
                stage = TaskStage.PLANNING,
                currentStep = null,
                expectedAction = ExpectedAction.NONE,
                status = TaskStatus.ACTIVE
            )
            activeTaskId?.let { currentId ->
                replaceTask(
                    requireTaskItem(currentId).copy(status = TaskStatus.PAUSED)
                )
            }
            taskItems += newItem
            activeTaskId = newItem.id
            taskState = it
        }

    override fun updateTaskStage(stage: TaskStage): TaskState =
        requireTaskState().copy(stage = stage).also { taskState = it }

    override fun updateTaskStep(step: String): TaskState =
        requireTaskState().copy(currentStep = step).also { taskState = it }

    override fun updateTaskExpectedAction(action: ExpectedAction): TaskState =
        requireTaskState().copy(expectedAction = action).also { taskState = it }

    override fun pauseTask(): TaskState =
        requireTaskState().copy(status = TaskStatus.PAUSED).also {
            taskState = it
            activeTaskId?.let { id ->
                replaceTask(requireTaskItem(id).copy(status = TaskStatus.PAUSED))
            }
        }

    override fun resumeTask(): TaskState =
        requireTaskState().copy(status = TaskStatus.ACTIVE).also {
            taskState = it
            activeTaskId?.let { id ->
                replaceTask(requireTaskItem(id).copy(status = TaskStatus.ACTIVE))
            }
        }

    override fun switchTask(taskId: String): TaskState = activateTask(taskId)

    override fun resumeTask(taskId: String): TaskState = activateTask(taskId).also { resumed ->
        replaceTask(requireTaskItem(taskId).copy(status = TaskStatus.ACTIVE))
        taskState = resumed
    }

    override fun completeTask(): TaskState =
        requireTaskState().copy(status = TaskStatus.DONE).also {
            taskState = it
            activeTaskId?.let { id ->
                replaceTask(requireTaskItem(id).copy(status = TaskStatus.DONE))
            }
        }

    override fun completeTask(taskId: String): TaskState =
        requireTaskItem(taskId).copy(status = TaskStatus.DONE).also { completed ->
            replaceTask(completed)
            if (activeTaskId == taskId) {
                activeTaskId = taskItems.firstOrNull { it.id != taskId && it.status == TaskStatus.ACTIVE }?.id
                taskState = activeTask()?.toTaskState()
            }
        }.toTaskState()

    override fun clearTask() {
        taskState = null
    }

    override fun inspectPendingMemory(): PendingMemoryState = currentPendingState

    override fun approvePendingMemory(candidateIds: List<String>): PendingMemoryActionResult =
        PendingMemoryActionResult(
            affectedIds = listOf("p1"),
            pendingState = PendingMemoryState()
        )

    override fun rejectPendingMemory(candidateIds: List<String>): PendingMemoryActionResult =
        PendingMemoryActionResult(
            affectedIds = listOf("p1"),
            pendingState = PendingMemoryState()
        )

    override fun editPendingMemory(candidateId: String, edit: PendingMemoryEdit): PendingMemoryState =
        currentPendingState

    override fun memoryCategories(layer: MemoryLayer): List<String> =
        when (layer) {
            MemoryLayer.WORKING -> listOf("goal", "constraint")
            MemoryLayer.LONG_TERM -> listOf("communication_style")
            MemoryLayer.SHORT_TERM -> emptyList()
        }

    override fun addMemoryNote(layer: MemoryLayer, category: String, content: String): ManagedMemoryNoteResult =
        ManagedMemoryNoteResult(
            note = MemoryNote(id = "n1", category = category, content = content),
            state = memorySnapshot().state
        ).also {
            addedMemoryNotes += Triple(layer, category, content)
        }

    override fun editMemoryNote(layer: MemoryLayer, noteId: String, edit: ManagedMemoryNoteEdit): ManagedMemoryNoteResult =
        ManagedMemoryNoteResult(
            note = MemoryNote(id = noteId, category = "goal", content = "Обновлённая заметка"),
            state = memorySnapshot().state
        )

    override fun deleteMemoryNote(layer: MemoryLayer, noteId: String): ManagedMemoryNoteResult =
        ManagedMemoryNoteResult(
            note = MemoryNote(id = noteId, category = "goal", content = "Удалённая заметка"),
            state = memorySnapshot().state
        )

    override fun addProfileNote(category: String, content: String): ManagedMemoryNoteResult =
        ManagedMemoryNoteResult(
            note = MemoryNote(id = "n-profile", category = category, content = content),
            state = memorySnapshot().state
        ).also {
            addedProfileNotes += category to content
        }

    override fun editProfileNote(noteId: String, edit: ManagedMemoryNoteEdit): ManagedMemoryNoteResult =
        ManagedMemoryNoteResult(
            note = MemoryNote(id = noteId, category = "communication_style", content = "Обновлённый профиль"),
            state = memorySnapshot().state
        )

    override fun deleteProfileNote(noteId: String): ManagedMemoryNoteResult =
        ManagedMemoryNoteResult(
            note = MemoryNote(id = noteId, category = "communication_style", content = "Удалённый профиль"),
            state = memorySnapshot().state
        )

    override fun <TCapability : AgentCapability> capability(capabilityType: Class<TCapability>): TCapability? = null

    fun requireTaskState(): TaskState =
        requireNotNull(taskState) { "Текущая задача в тестовом агенте не создана." }

    private val taskItems = mutableListOf(
        TaskItem(
            id = "task-1",
            title = "Реализовать task subsystem",
            stage = TaskStage.EXECUTION,
            currentStep = "Подключить CLI",
            expectedAction = ExpectedAction.AGENT_EXECUTION,
            status = TaskStatus.ACTIVE
        ),
        TaskItem(
            id = "task-2",
            title = "Подготовить smoke-check",
            stage = TaskStage.PLANNING,
            currentStep = "Собрать сценарий",
            expectedAction = ExpectedAction.USER_INPUT,
            status = TaskStatus.PAUSED
        )
    )

    private var activeTaskId: String? = "task-1"

    private fun replaceTask(task: TaskItem) {
        val index = taskItems.indexOfFirst { it.id == task.id }
        require(index >= 0) { "Task ${task.id} not found" }
        taskItems[index] = task
    }

    private fun requireTaskItem(taskId: String): TaskItem =
        taskItems.firstOrNull { it.id == taskId } ?: error("Task $taskId not found")

    private fun activateTask(taskId: String): TaskState {
        val currentActiveId = activeTaskId
        val targetTask = requireTaskItem(taskId)
        if (currentActiveId != null && currentActiveId != taskId) {
            replaceTask(requireTaskItem(currentActiveId).copy(status = TaskStatus.PAUSED))
        }
        val activated = targetTask.copy(status = TaskStatus.ACTIVE)
        replaceTask(activated)
        activeTaskId = taskId
        taskState = activated.toTaskState()
        return taskState!!
    }

    companion object {
        fun memorySnapshot(): MemorySnapshot =
            MemorySnapshot(
                state = MemoryState(),
                shortTermStrategyType = MemoryStrategyType.NO_COMPRESSION
            )

        fun pendingMemory(): PendingMemoryState =
            PendingMemoryState(
                candidates = listOf(
                    PendingMemoryCandidate(
                        id = "p1",
                        targetLayer = MemoryLayer.LONG_TERM,
                        category = "communication_style",
                        content = "Отвечай кратко",
                        sourceRole = ChatRole.USER,
                        sourceMessage = "Отвечай кратко"
                    )
                ),
                nextId = 2
            )
    }
}

private class FakeLanguageModel(
    model: String
) : LanguageModel {
    override val info = LanguageModelInfo(
        name = "FakeLanguageModel",
        model = model
    )

    override val tokenCounter = null

    override fun complete(messages: List<ChatMessage>): LanguageModelResponse =
        error("Не должен вызываться в этом тесте.")
}
