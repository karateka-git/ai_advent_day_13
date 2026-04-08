import agent.core.Agent
import agent.lifecycle.AgentLifecycleListener
import agent.lifecycle.AppEventLifecycleListener
import agent.memory.strategy.MemoryStrategyFactory
import agent.memory.strategy.MemoryStrategyOption
import agent.memory.strategy.MemoryStrategyType
import app.output.AppEvent
import app.output.AppEventSink
import app.output.CompositeAppEventSink
import app.output.DebugTraceListener
import java.nio.file.Path
import bootstrap.AgentFactory
import bootstrap.ApplicationBootstrap
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import llm.core.LanguageModel
import llm.core.LanguageModelFactory
import llm.core.model.ChatRole
import ui.cli.CliCommands
import ui.cli.CliRenderer
import ui.cli.CliSessionController
import ui.cli.CliSessionControllerResult
import ui.cli.CliSessionState

private val consoleReader = BufferedReader(
    InputStreamReader(System.`in`, detectConsoleCharset())
)
private val systemConsole = System.console()

/**
 * Точка входа CLI-приложения.
 *
 * Приложение загружает конфигурацию, выбирает LLM-провайдер, создаёт агента
 * со стратегией памяти по умолчанию и затем запускает интерактивный цикл чата.
 */
fun main() {
    val runtime = ApplicationBootstrap.createRuntime()
    val agentFactory = AgentFactory(
        config = runtime.config,
        httpClient = runtime.httpClient
    )
    val appEventSink: AppEventSink = buildAppEventSink()
    val lifecycleListener: AgentLifecycleListener = AppEventLifecycleListener(appEventSink)
    val languageModel = runtime.languageModel
    ApplicationBootstrap.warmUpTokenCounter(
        languageModel = languageModel,
        onStarted = lifecycleListener::onModelWarmupStarted,
        onFinished = lifecycleListener::onModelWarmupFinished
    )

    val defaultMemoryStrategyOption = MemoryStrategyFactory.defaultOption()
    val agent: Agent<String> = createAgent(
        agentFactory = agentFactory,
        languageModel = languageModel,
        lifecycleListener = lifecycleListener,
        strategyType = defaultMemoryStrategyOption.type
    )
    val sessionController = CliSessionController(
        initialState = CliSessionState(
            modelId = runtime.selectedModelId,
            languageModel = languageModel,
            agent = agent,
            memoryStrategyOption = defaultMemoryStrategyOption
        ),
        config = runtime.config,
        httpClient = runtime.httpClient,
        lifecycleListener = lifecycleListener,
        appEventSink = appEventSink,
        showModelPrompt = shouldShowModelPrompt(),
        createLanguageModel = LanguageModelFactory::create,
        createAgent = { model, listener, strategyType ->
            createAgent(agentFactory, model, listener, strategyType)
        },
        selectMemoryStrategy = { selectMemoryStrategyOption(appEventSink) },
        warmUpLanguageModel = { model, listener ->
            ApplicationBootstrap.warmUpTokenCounter(
                languageModel = model,
                onStarted = listener::onModelWarmupStarted,
                onFinished = listener::onModelWarmupFinished
            )
        }
    )

    appEventSink.emit(
        AppEvent.SessionStarted
    )
    appEventSink.emit(
        AppEvent.AgentInfoAvailable(
            info = sessionController.state.agent.info,
            strategy = defaultMemoryStrategyOption
        )
    )

    while (true) {
        appEventSink.emit(AppEvent.UserInputPrompt(ChatRole.USER))
        val prompt = readConsoleLine()?.trim() ?: break
        appEventSink.emit(AppEvent.UserInputReceived(ChatRole.USER, prompt))

        when (sessionController.handle(prompt)) {
            CliSessionControllerResult.Continue -> continue
            CliSessionControllerResult.ExitRequested -> break
        }
    }
}

private fun buildAppEventSink(): AppEventSink {
    val sinks = mutableListOf<AppEventSink>(CliRenderer())
    debugTracePath()?.let { sinks += DebugTraceListener(it) }
    return if (sinks.size == 1) sinks.single() else CompositeAppEventSink(sinks)
}

private fun shouldShowModelPrompt(): Boolean =
    System.getProperty("debug.showModelPrompt")
        ?.toBooleanStrictOrNull()
        ?: System.getenv("DEBUG_SHOW_MODEL_PROMPT")
            ?.toBooleanStrictOrNull()
        ?: false

private fun debugTracePath(): Path? =
    System.getProperty("debug.traceFile")
        ?.takeIf(String::isNotBlank)
        ?.let(Path::of)
        ?: System.getenv("DEBUG_TRACE_FILE")
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)

/**
 * Создаёт новый экземпляр агента для выбранной модели и стратегии памяти.
 */
private fun createAgent(
    agentFactory: AgentFactory,
    languageModel: LanguageModel,
    lifecycleListener: AgentLifecycleListener,
    strategyType: MemoryStrategyType
): Agent<String> =
    agentFactory.create(
        languageModel = languageModel,
        lifecycleListener = lifecycleListener,
        strategyType = strategyType
    )

/**
 * Предлагает пользователю выбрать одну из доступных стратегий памяти при смене модели.
 */
private fun selectMemoryStrategyOption(appEventSink: AppEventSink): MemoryStrategyOption {
    val options = MemoryStrategyFactory.availableOptions()
    appEventSink.emit(AppEvent.MemoryStrategySelectionRequested(options))

    while (true) {
        appEventSink.emit(AppEvent.MemoryStrategySelectionPromptRequested(options.size))
        val selection = readConsoleLine()?.trim().orEmpty()
        val index = selection.toIntOrNull()

        if (index != null && index in 1..options.size) {
            val option = options[index - 1]
            appEventSink.emit(AppEvent.MemoryStrategySelected(option))
            return option
        }

        appEventSink.emit(AppEvent.MemoryStrategySelectionRejected)
    }
}

/**
 * Определяет кодировку, используемую текущей консольной сессией.
 */
internal fun detectConsoleCharset(
    hasConsole: Boolean = System.console() != null,
    nativeEncoding: String? = System.getProperty("native.encoding")
): Charset {
    if (!hasConsole) {
        return StandardCharsets.UTF_8
    }

    return if (nativeEncoding.isNullOrBlank()) {
        Charset.defaultCharset()
    } else {
        Charset.forName(nativeEncoding)
    }
}

/**
 * Очищает ввод от BOM и его искажённого текстового следа в начале строки.
 */
internal fun sanitizeConsoleInput(input: String): String =
    input
        .removePrefix("\uFEFF")
        .removePrefix("ï»¿")
        .removePrefix("п»ї")

/**
 * Читает одну строку из консоли, предпочитая нативный API консоли, если он доступен.
 */
private fun readConsoleLine(): String? =
    (systemConsole?.readLine() ?: consoleReader.readLine())
        ?.let(::sanitizeConsoleInput)
