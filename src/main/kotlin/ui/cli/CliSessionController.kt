package ui.cli

import agent.capability.capability
import agent.core.Agent
import agent.lifecycle.AgentLifecycleListener
import agent.memory.model.ManagedMemoryNoteEdit
import agent.memory.model.MemoryLayer
import agent.memory.model.PendingMemoryEdit
import agent.memory.strategy.MemoryStrategyOption
import agent.memory.strategy.MemoryStrategyType
import agent.memory.strategy.branching.BranchingCapability
import app.output.AppEvent
import app.output.AppEventSink
import app.output.HelpCommandDescriptor
import app.output.HelpCommandGroup
import java.net.http.HttpClient
import java.util.Properties
import llm.core.LanguageModel
import llm.core.LanguageModelFactory
import llm.core.model.ChatRole
import llm.core.model.LanguageModelOption

/**
 * Исполняет команды CLI и обычные пользовательские prompt в рамках одной сессии.
 */
class CliSessionController(
    initialState: CliSessionState,
    private val config: Properties,
    private val httpClient: HttpClient,
    private val lifecycleListener: AgentLifecycleListener,
    private val appEventSink: AppEventSink,
    private val commandParser: CliCommandParser = CliCommandParser(),
    private val createLanguageModel: (String, Properties, HttpClient) -> LanguageModel,
    private val availableModelsProvider: (Properties) -> List<LanguageModelOption> = LanguageModelFactory::availableModels,
    private val createAgent: (LanguageModel, AgentLifecycleListener, MemoryStrategyType) -> Agent<String>,
    private val selectMemoryStrategy: () -> MemoryStrategyOption,
    private val warmUpLanguageModel: (LanguageModel, AgentLifecycleListener) -> Unit
) {
    /**
     * Актуальное состояние текущей CLI-сессии.
     */
    var state: CliSessionState = initialState
        private set

    /**
     * Обрабатывает один ввод пользователя: встроенную команду или обычный prompt.
     */
    fun handle(input: String): CliSessionControllerResult =
        when (val command = commandParser.parse(input)) {
            CliCommand.Empty -> CliSessionControllerResult.Continue
            CliCommand.ShowHelp -> {
                appEventSink.emit(
                    AppEvent.CommandsAvailable(
                        title = "Доступные команды",
                        groups = GeneralCliCatalog.helpGroups
                    )
                )
                CliSessionControllerResult.Continue
            }

            CliCommand.Exit -> {
                appEventSink.emit(AppEvent.SessionFinished)
                CliSessionControllerResult.ExitRequested
            }

            is CliCommand.InvalidCommand -> {
                appEventSink.emit(AppEvent.RequestFailed(formatInvalidCommandReason(command.reason)))
                CliSessionControllerResult.Continue
            }

            CliCommand.Clear -> {
                state.agent.clearContext()
                appEventSink.emit(AppEvent.ContextCleared)
                CliSessionControllerResult.Continue
            }

            CliCommand.ShowModels -> {
                appEventSink.emit(
                    AppEvent.ModelsAvailable(
                        options = availableModelsProvider(config),
                        currentModelId = state.modelId
                    )
                )
                CliSessionControllerResult.Continue
            }

            is CliCommand.ShowMemory -> {
                appEventSink.emit(
                    AppEvent.MemoryStateAvailable(
                        snapshot = state.agent.inspectMemory(),
                        selectedLayer = command.layer
                    )
                )
                CliSessionControllerResult.Continue
            }

            is CliCommand.ShowMemoryCategories -> {
                appEventSink.emit(
                    AppEvent.CommandsAvailable(
                        title = "Категории памяти",
                        groups = memoryCategoryGroups(command.layer)
                    )
                )
                CliSessionControllerResult.Continue
            }

            CliCommand.ShowPendingMemory -> {
                appEventSink.emit(
                    AppEvent.PendingMemoryAvailable(
                        pending = state.agent.inspectPendingMemory()
                    )
                )
                CliSessionControllerResult.Continue
            }

            CliCommand.ShowPendingMemoryCommands -> {
                appEventSink.emit(
                    AppEvent.PendingMemoryCommandsAvailable(
                        commands = PendingMemoryCliCatalog.helpCommands
                    )
                )
                CliSessionControllerResult.Continue
            }

            is CliCommand.ApprovePendingMemory -> {
                try {
                    val result = state.agent.approvePendingMemory(command.ids)
                    appEventSink.emit(
                        AppEvent.PendingMemoryActionCompleted(
                            message =
                                if (command.ids.isEmpty()) {
                                    "Подтверждены все pending-кандидаты: ${result.affectedIds.size}"
                                } else {
                                    "Подтверждены pending-кандидаты: ${result.affectedIds.joinToString(", ")}"
                                },
                            pending = result.pendingState
                        )
                    )
                } catch (error: Exception) {
                    appEventSink.emit(AppEvent.RequestFailed(error.message))
                }
                CliSessionControllerResult.Continue
            }

            is CliCommand.RejectPendingMemory -> {
                try {
                    val result = state.agent.rejectPendingMemory(command.ids)
                    appEventSink.emit(
                        AppEvent.PendingMemoryActionCompleted(
                            message =
                                if (command.ids.isEmpty()) {
                                    "Отклонены все pending-кандидаты: ${result.affectedIds.size}"
                                } else {
                                    "Отклонены pending-кандидаты: ${result.affectedIds.joinToString(", ")}"
                                },
                            pending = result.pendingState
                        )
                    )
                } catch (error: Exception) {
                    appEventSink.emit(AppEvent.RequestFailed(error.message))
                }
                CliSessionControllerResult.Continue
            }

            is CliCommand.EditPendingMemory -> {
                try {
                    val updatedPending = state.agent.editPendingMemory(
                        candidateId = command.id,
                        edit = parsePendingEdit(command.field, command.value)
                    )
                    appEventSink.emit(
                        AppEvent.PendingMemoryActionCompleted(
                            message = "Pending-кандидат ${command.id} обновлён.",
                            pending = updatedPending
                        )
                    )
                } catch (error: Exception) {
                    appEventSink.emit(AppEvent.RequestFailed(error.message))
                }
                CliSessionControllerResult.Continue
            }

            is CliCommand.AddMemoryNote -> {
                try {
                    val result = state.agent.addMemoryNote(command.layer, command.category, command.content)
                    appEventSink.emit(
                        AppEvent.CommandCompleted(
                            "Добавлена заметка ${result.note.id} в слой ${layerLabel(command.layer)}."
                        )
                    )
                } catch (error: Exception) {
                    appEventSink.emit(AppEvent.RequestFailed(error.message))
                }
                CliSessionControllerResult.Continue
            }

            is CliCommand.EditMemoryNote -> {
                try {
                    val result = state.agent.editMemoryNote(command.layer, command.id, command.edit)
                    appEventSink.emit(
                        AppEvent.CommandCompleted(
                            "Заметка ${result.note.id} в слое ${layerLabel(command.layer)} обновлена."
                        )
                    )
                } catch (error: Exception) {
                    appEventSink.emit(AppEvent.RequestFailed(error.message))
                }
                CliSessionControllerResult.Continue
            }

            is CliCommand.DeleteMemoryNote -> {
                try {
                    val result = state.agent.deleteMemoryNote(command.layer, command.id)
                    appEventSink.emit(
                        AppEvent.CommandCompleted(
                            "Заметка ${result.note.id} удалена из слоя ${layerLabel(command.layer)}."
                        )
                    )
                } catch (error: Exception) {
                    appEventSink.emit(AppEvent.RequestFailed(error.message))
                }
                CliSessionControllerResult.Continue
            }

            is CliCommand.CreateCheckpoint -> {
                try {
                    val branchingCapability = state.agent.capability<BranchingCapability>()
                        ?: error("Команды ветвления доступны только для стратегии Branching.")
                    appEventSink.emit(
                        AppEvent.CheckpointCreated(
                            branchingCapability.createCheckpoint(command.name)
                        )
                    )
                } catch (error: Exception) {
                    appEventSink.emit(AppEvent.RequestFailed(error.message))
                }
                CliSessionControllerResult.Continue
            }

            CliCommand.ShowBranches -> {
                try {
                    val branchingCapability = state.agent.capability<BranchingCapability>()
                        ?: error("Команды ветвления доступны только для стратегии Branching.")
                    appEventSink.emit(AppEvent.BranchStatusAvailable(branchingCapability.branchStatus()))
                } catch (error: Exception) {
                    appEventSink.emit(AppEvent.RequestFailed(error.message))
                }
                CliSessionControllerResult.Continue
            }

            is CliCommand.CreateBranch -> {
                try {
                    val branchingCapability = state.agent.capability<BranchingCapability>()
                        ?: error("Команды ветвления доступны только для стратегии Branching.")
                    appEventSink.emit(AppEvent.BranchCreated(branchingCapability.createBranch(command.name)))
                } catch (error: Exception) {
                    appEventSink.emit(AppEvent.RequestFailed(error.message))
                }
                CliSessionControllerResult.Continue
            }

            is CliCommand.SwitchBranch -> {
                try {
                    val branchingCapability = state.agent.capability<BranchingCapability>()
                        ?: error("Команды ветвления доступны только для стратегии Branching.")
                    appEventSink.emit(AppEvent.BranchSwitched(branchingCapability.switchBranch(command.name)))
                } catch (error: Exception) {
                    appEventSink.emit(AppEvent.RequestFailed(error.message))
                }
                CliSessionControllerResult.Continue
            }

            is CliCommand.SwitchModel -> {
                switchModel(command.modelId)
                CliSessionControllerResult.Continue
            }

            is CliCommand.UserPrompt -> {
                try {
                    val pendingBefore = state.agent.inspectPendingMemory().candidates.size
                    appEventSink.emit(AppEvent.TokenPreviewAvailable(state.agent.previewTokenStats(command.value)))
                    appEventSink.emit(AppEvent.ModelRequestStarted)
                    val response = try {
                        state.agent.ask(command.value)
                    } finally {
                        appEventSink.emit(AppEvent.ModelRequestFinished)
                    }
                    appEventSink.emit(
                        AppEvent.AssistantResponseAvailable(
                            role = ChatRole.ASSISTANT,
                            content = response.content,
                            tokenStats = response.tokenStats
                        )
                    )
                    val pendingAfter = state.agent.inspectPendingMemory()
                    if (pendingAfter.candidates.size > pendingBefore) {
                        appEventSink.emit(
                            AppEvent.PendingMemoryAvailable(
                                pending = pendingAfter,
                                reason = "Есть кандидаты на сохранение в память. Посмотри их командой ${PendingMemoryCliCatalog.SHOW}."
                            )
                        )
                    }
                } catch (error: Exception) {
                    appEventSink.emit(AppEvent.RequestFailed(error.message))
                }

                CliSessionControllerResult.Continue
            }
        }

    private fun switchModel(requestedModelId: String) {
        try {
            val languageModel = createLanguageModel(
                requestedModelId,
                config,
                httpClient
            )
            warmUpLanguageModel(languageModel, lifecycleListener)
            val selectedStrategy = selectMemoryStrategy()
            val agent = createAgent(
                languageModel,
                lifecycleListener,
                selectedStrategy.type
            )
            state = state.copy(
                modelId = requestedModelId,
                languageModel = languageModel,
                agent = agent,
                memoryStrategyOption = selectedStrategy
            )
            appEventSink.emit(AppEvent.ModelChanged)
            appEventSink.emit(
                AppEvent.AgentInfoAvailable(
                    info = agent.info,
                    strategy = selectedStrategy
                )
            )
        } catch (error: Exception) {
            appEventSink.emit(AppEvent.ModelSwitchFailed(error.message))
        }
    }

    private fun parsePendingEdit(field: String, value: String): PendingMemoryEdit =
        when (field.lowercase()) {
            "text" -> PendingMemoryEdit.UpdateText(value)
            "layer" -> PendingMemoryEdit.UpdateLayer(parseEditableLayer(value))
            "category" -> PendingMemoryEdit.UpdateCategory(value)
            else -> error("Поддерживаются только поля text, layer и category.")
        }

    private fun parseEditableLayer(value: String): MemoryLayer =
        when (value.trim().lowercase()) {
            "working", "work", "рабочая" -> MemoryLayer.WORKING
            "long", "long-term", "долговременная" -> MemoryLayer.LONG_TERM
            else -> error("Для pending-кандидата поддерживаются только слои working и long.")
        }

    private fun memoryCategoryGroups(layer: MemoryLayer?): List<HelpCommandGroup> {
        val layers = layer?.let(::listOf) ?: listOf(MemoryLayer.WORKING, MemoryLayer.LONG_TERM)
        return layers.map { selectedLayer ->
            HelpCommandGroup(
                title = "Категории слоя ${layerLabel(selectedLayer)}",
                commands = state.agent.memoryCategories(selectedLayer).map { category ->
                    HelpCommandDescriptor("/memory add ${layerCommandValue(selectedLayer)} $category <текст>", category)
                }
            )
        }
    }

    private fun layerCommandValue(layer: MemoryLayer): String =
        when (layer) {
            MemoryLayer.WORKING -> "working"
            MemoryLayer.LONG_TERM -> "long"
            MemoryLayer.SHORT_TERM -> "short"
        }

    private fun layerLabel(layer: MemoryLayer): String =
        when (layer) {
            MemoryLayer.WORKING -> "рабочая память"
            MemoryLayer.LONG_TERM -> "долговременная память"
            MemoryLayer.SHORT_TERM -> "краткосрочная память"
        }

    private fun formatInvalidCommandReason(reason: InvalidCliCommandReason): String =
        when (reason) {
            is InvalidCliCommandReason.UnknownCommand ->
                "Неизвестная команда: ${reason.command}. Для списка команд используйте ${CliCommands.HELP}."
            is InvalidCliCommandReason.Usage -> "Используйте: ${reason.usage}."
            is InvalidCliCommandReason.PendingEditUnsupportedField -> {
                val supportedFields = reason.allowedFields.joinToString(", ")
                "Поле редактирования должно быть одним из: $supportedFields."
            }
        }
}

/**
 * Результат обработки одного шага CLI-сессии.
 */
sealed interface CliSessionControllerResult {
    /**
     * Сессия продолжается, можно читать следующий ввод.
     */
    data object Continue : CliSessionControllerResult

    /**
     * Пользователь завершил работу сессии.
     */
    data object ExitRequested : CliSessionControllerResult
}
