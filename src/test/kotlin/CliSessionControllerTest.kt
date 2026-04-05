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
import agent.memory.strategy.MemoryStrategyOption
import agent.memory.strategy.MemoryStrategyType
import app.output.AppEvent
import app.output.AppEventSink
import app.output.HelpCommandGroup
import java.net.http.HttpClient
import java.nio.file.Path
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `shows memory through current agent`() {
        val sink = RecordingAppEventSink()
        val controller = createController(sink = sink)

        val result = controller.handle("/memory long")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(
            listOf<AppEvent>(
                AppEvent.MemoryStateAvailable(
                    snapshot = FakeAgent.memorySnapshot(),
                    selectedLayer = MemoryLayer.LONG_TERM
                )
            ),
            sink.events
        )
    }

    @Test
    fun `shows pending candidates through current agent`() {
        val sink = RecordingAppEventSink()
        val controller = createController(sink = sink)

        val result = controller.handle("/memory pending")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(
            listOf<AppEvent>(
                AppEvent.PendingMemoryAvailable(
                    pending = FakeAgent.pendingMemory()
                )
            ),
            sink.events
        )
    }

    @Test
    fun `shows pending commands help`() {
        val sink = RecordingAppEventSink()
        val controller = createController(sink = sink)

        val result = controller.handle("/memory pending info")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(
            listOf<AppEvent>(
                AppEvent.PendingMemoryCommandsAvailable(
                    commands = PendingMemoryCliCatalog.helpCommands
                )
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

    private fun createController(
        sink: RecordingAppEventSink = RecordingAppEventSink(),
        initialState: CliSessionState = initialState(),
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
}

private class RecordingAppEventSink : AppEventSink {
    val events = mutableListOf<AppEvent>()

    override fun emit(event: AppEvent) {
        events += event
    }
}

private class FakeAgent(
    private val previewTokenStats: AgentTokenStats = AgentTokenStats(),
    private val response: AgentResponse<String> = AgentResponse(
        content = "ok",
        tokenStats = AgentTokenStats()
    ),
    initialPendingState: PendingMemoryState = pendingMemory(),
    private val pendingStateAfterAsk: PendingMemoryState = initialPendingState
) : Agent<String> {
    private var currentPendingState: PendingMemoryState = initialPendingState

    override val info: AgentInfo = AgentInfo(
        name = "TestAgent",
        description = "Тестовый агент",
        model = "test-model"
    )
    override val responseFormat: ResponseFormat<String> = TextResponseFormat

    override fun previewTokenStats(userPrompt: String): AgentTokenStats = previewTokenStats

    override fun ask(userPrompt: String): AgentResponse<String> {
        currentPendingState = pendingStateAfterAsk
        return response
    }

    override fun clearContext() = Unit

    override fun replaceContextFromFile(sourcePath: Path) = Unit

    override fun inspectMemory(): MemorySnapshot = memorySnapshot()

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
        )

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

    override fun <TCapability : AgentCapability> capability(capabilityType: Class<TCapability>): TCapability? = null

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
