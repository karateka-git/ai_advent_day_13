package app.output

import agent.core.AgentTokenStats
import agent.memory.model.MemoryLayer
import agent.memory.model.MemoryNote
import agent.memory.model.MemoryOwnerType
import agent.memory.model.MemorySnapshot
import agent.memory.model.PendingMemoryState
import agent.task.model.ExpectedAction
import agent.task.model.TaskItem
import agent.task.model.TaskState
import agent.task.model.TaskStatus
import agent.task.model.TaskStages
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

/**
 * Записывает структурированный debug trace на основе [AppEvent].
 *
 * Используется для smoke/debug-сценариев и не зависит от конкретного UI-рендера.
 */
class DebugTraceListener(
    private val outputPath: Path,
    private val json: Json = Json { encodeDefaults = true }
) : AppEventSink {
    init {
        outputPath.parent?.let(Files::createDirectories)
        Files.deleteIfExists(outputPath)
    }

    override fun emit(event: AppEvent) {
        val record = toRecord(event) ?: return
        synchronized(this) {
            Files.writeString(
                outputPath,
                json.encodeToString(record) + System.lineSeparator(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            )
        }
    }

    private fun toRecord(event: AppEvent): DebugTraceRecord? =
        when (event) {
            AppEvent.SessionStarted -> record(
                kind = "session_started",
                title = "Старт",
                lines = listOf(
                    "Чат готов.",
                    "Для списка команд введи '/help'."
                )
            )

            is AppEvent.CommandsAvailable -> record(
                kind = "commands",
                title = event.title,
                lines = buildList {
                    event.groups.forEachIndexed { index, group ->
                        if (index > 0) add("")
                        add(group.title)
                        group.commands.forEach { command ->
                            add("  ${command.command}")
                            add("    ${command.description}")
                        }
                    }
                }
            )

            is AppEvent.MemoryStrategySelectionRequested -> record(
                kind = "memory_strategy_selection",
                title = "Выбор стратегии памяти",
                lines = buildList {
                    add("Выберите стратегию памяти для нового агента:")
                    event.options.forEachIndexed { index, option ->
                        add("${index + 1}. ${option.displayName} - ${option.description}")
                    }
                }
            )

            is AppEvent.MemoryStrategySelectionPromptRequested -> null

            is AppEvent.MemoryStrategySelected -> record(
                kind = "memory_strategy_selected",
                title = "Стратегия выбрана",
                lines = listOf(
                    "Стратегия: ${event.option.displayName}",
                    "Как работает: ${event.option.description}"
                )
            )

            AppEvent.MemoryStrategySelectionRejected -> record(
                kind = "command_result",
                title = "Результат команды",
                lines = listOf("Некорректный выбор. Попробуйте ещё раз.")
            )

            is AppEvent.AgentInfoAvailable -> record(
                kind = "agent_info",
                title = "Агент",
                lines = listOf(
                    "Агент: ${event.info.name}",
                    "Описание: ${event.info.description}",
                    "Модель: ${event.info.model}",
                    "Стратегия памяти: ${event.strategy.displayName}",
                    "Поведение стратегии: ${event.strategy.description}"
                )
            )

            is AppEvent.ModelsAvailable -> record(
                kind = "models",
                title = "Доступные модели",
                lines = event.options.map { option ->
                    buildString {
                        val marker = if (option.id == event.currentModelId) "*" else " "
                        append(marker)
                        append(" ")
                        append(option.id)
                        append(" - ")
                        append(option.displayName)
                        if (!option.isConfigured) {
                            append(" (недоступна: ${option.unavailableReason})")
                        }
                    }
                }
            )

            is AppEvent.MemoryStateAvailable -> record(
                kind = "memory_state",
                title = "Память",
                lines = buildMemoryLines(event.snapshot, event.selectedLayer)
            )

            is AppEvent.UsersAvailable -> record(
                kind = "users",
                title = "Пользователи",
                lines = event.users.map { user ->
                    val marker = if (user.id == event.activeUserId) "*" else " "
                    "$marker ${user.id} (${user.displayName})"
                }
            )

            is AppEvent.UserProfileAvailable -> record(
                kind = "user_profile",
                title = "Профиль пользователя",
                lines = buildList {
                    add("Активный пользователь: ${event.user.displayName} (${event.user.id})")
                    if (event.notes.isEmpty()) {
                        add("(пусто)")
                    } else {
                        event.notes.forEach { add(formatManagedNote(it)) }
                    }
                }
            )

            is AppEvent.TaskStateAvailable -> record(
                kind = "task_state",
                title = "Задача",
                lines = buildTaskLines(event.task)
            )

            is AppEvent.TaskListAvailable -> record(
                kind = "task_list",
                title = "Задачи",
                lines = buildTaskListLines(event.tasks, event.activeTaskId)
            )

            is AppEvent.PendingMemoryAvailable ->
                if (event.reason != null) {
                    record(
                        kind = "command_result",
                        title = "Результат команды",
                        lines = listOf(event.reason)
                    )
                } else {
                    record(
                        kind = "pending_memory",
                        title = "Pending-память",
                        lines = buildPendingLines(event.pending)
                    )
                }

            is AppEvent.PendingMemoryActionCompleted -> record(
                kind = "command_result",
                title = "Результат команды",
                lines = event.message.lines()
            )

            is AppEvent.PendingMemoryCommandsAvailable -> record(
                kind = "commands",
                title = "Команды для pending-памяти",
                lines = buildList {
                    add("Pending-память")
                    event.commands.forEach { command ->
                        add("  ${command.command}")
                        add("    ${command.description}")
                    }
                }
            )

            is AppEvent.CheckpointCreated -> record(
                kind = "checkpoint_created",
                title = "Checkpoint создан",
                lines = listOf(
                    "Имя: ${event.info.name}",
                    "Исходная ветка: ${event.info.sourceBranchName}"
                )
            )

            is AppEvent.BranchCreated -> record(
                kind = "branch_created",
                title = "Ветка создана",
                lines = listOf(
                    "Имя: ${event.info.name}",
                    "Источник: ${event.info.sourceCheckpointName ?: "без checkpoint"}"
                )
            )

            is AppEvent.BranchSwitched -> record(
                kind = "branch_switched",
                title = "Переключение ветки",
                lines = listOf("Активная ветка: ${event.info.name}")
            )

            is AppEvent.BranchStatusAvailable -> record(
                kind = "branch_status",
                title = "Статус веток",
                lines = buildList {
                    add("Активная ветка: ${event.status.activeBranchName}")
                    add("Последний checkpoint: ${event.status.latestCheckpointName ?: "нет"}")
                    add("Ветки:")
                    event.status.branches.forEach { branch ->
                        val marker = if (branch.isActive) "*" else " "
                        add("$marker ${branch.name} (checkpoint: ${branch.sourceCheckpointName ?: "нет"})")
                    }
                }
            )

            is AppEvent.UserInputPrompt -> null

            is AppEvent.UserInputReceived -> record(
                kind = "user_input",
                title = event.role.displayName,
                lines = event.content.lines()
            )

            is AppEvent.TaskBehaviorNotice -> record(
                kind = "task_behavior_notice",
                title = "Контекст задачи",
                lines = event.message.lines()
            )

            is AppEvent.AssistantResponseAvailable -> record(
                kind = "assistant_response",
                title = event.role.displayName,
                lines = buildList {
                    addAll(event.content.lines())
                    formatResponseTokenLines(event.tokenStats)?.let { stats ->
                        add("")
                        addAll(stats)
                    }
                }
            )

            is AppEvent.TokenPreviewAvailable -> {
                val lines = formatPreviewTokenLines(event.tokenStats) ?: return null
                record(
                    kind = "token_preview",
                    title = "Оценка перед запросом",
                    lines = lines
                )
            }

            is AppEvent.ModelPromptAvailable -> record(
                kind = "model_prompt",
                title = "Запрос к модели",
                lines = event.prompt.lines()
            )

            AppEvent.ContextCleared -> commandResult("Контекст очищен. Системное сообщение сохранено.")
            AppEvent.ModelChanged -> commandResult("Текущая модель изменена.")
            is AppEvent.ModelSwitchFailed -> commandResult("Не удалось переключить модель: ${event.details}")
            is AppEvent.RequestFailed -> commandResult("Не удалось выполнить запрос: ${event.details}")
            is AppEvent.CommandCompleted -> commandResult(event.message)
            AppEvent.SessionFinished -> commandResult("Чат завершён.")
            AppEvent.ModelWarmupStarted -> null
            AppEvent.ModelWarmupFinished -> null
            AppEvent.ModelRequestStarted -> null
            AppEvent.ModelRequestFinished -> null
            AppEvent.ContextCompressionStarted -> null

            is AppEvent.ContextCompressionFinished -> {
                val message =
                    if (
                        event.stats.tokensBefore != null &&
                        event.stats.tokensAfter != null &&
                        event.stats.savedTokens != null
                    ) {
                        "Контекст сжат: ${event.stats.tokensBefore} -> ${event.stats.tokensAfter} токенов, экономия ${event.stats.savedTokens}"
                    } else {
                        "Контекст сжат."
                    }
                commandResult(message)
            }
        }

    private fun commandResult(message: String): DebugTraceRecord =
        record(
            kind = "command_result",
            title = "Результат команды",
            lines = message.lines()
        )

    private fun record(kind: String, title: String, lines: List<String>): DebugTraceRecord =
        DebugTraceRecord(
            kind = kind,
            title = title,
            lines = lines
        )

    private fun buildPendingLines(pending: PendingMemoryState): List<String> =
        buildList {
            add("Кандидаты в память:")
            if (pending.candidates.isEmpty()) {
                add("(пусто)")
            } else {
                pending.candidates.forEach { candidate ->
                    add("${candidate.id} [${layerTitle(candidate.targetLayer)}] ${noteCategoryTitle(candidate.category)}: ${candidate.content}")
                }
            }
            add("")
            add("Справка по командам: /memory pending info")
        }

    private fun buildTaskLines(task: TaskState?): List<String> =
        if (task == null) {
            listOf("(текущая задача не создана)")
        } else {
            listOf(
                "Название: ${task.title}",
                "Этап: ${TaskStages.definitionFor(task.stage).label}",
                "Статус: ${taskStatusLabel(task.status)}",
                "Ожидаемое действие: ${expectedActionLabel(task.expectedAction)}",
                "Описание этапа: ${TaskStages.definitionFor(task.stage).description}",
                "Текущий шаг: ${task.currentStep ?: "(не задан)"}"
            )
        }

    private fun buildTaskListLines(tasks: List<TaskItem>, activeTaskId: String?): List<String> =
        buildList {
            add("Активная задача: ${activeTaskId ?: "(нет)"}")
            if (tasks.isEmpty()) {
                add("(список задач пуст)")
            } else {
                tasks.forEach { task ->
                    val marker = if (task.id == activeTaskId) "*" else " "
                    add(
                        "$marker ${task.id} | ${task.title} | ${taskStatusLabel(task.status)} | ${TaskStages.definitionFor(task.stage).label}"
                    )
                }
            }
        }

    private fun buildMemoryLines(snapshot: MemorySnapshot, selectedLayer: MemoryLayer?): List<String> =
        buildList {
            memoryLayers(selectedLayer).forEachIndexed { index, layer ->
                if (index > 0) add("")
                add("Уровень памяти: ${layerTitle(layer)}")
                val layerLines = linesForLayer(snapshot, layer)
                if (layerLines.isEmpty()) {
                    add("(пусто)")
                } else {
                    layerLines.forEach { add("- $it") }
                }
            }
        }

    private fun memoryLayers(selectedLayer: MemoryLayer?): List<MemoryLayer> =
        selectedLayer?.let(::listOf) ?: listOf(MemoryLayer.SHORT_TERM, MemoryLayer.WORKING, MemoryLayer.LONG_TERM)

    private fun linesForLayer(snapshot: MemorySnapshot, layer: MemoryLayer): List<String> =
        when (layer) {
            MemoryLayer.SHORT_TERM -> buildShortTermLines(snapshot)
            MemoryLayer.WORKING -> snapshot.state.working.notes.map(::formatManagedNote)
            MemoryLayer.LONG_TERM -> snapshot.state.longTerm.notes.map(::formatManagedNote)
        }

    private fun buildShortTermLines(snapshot: MemorySnapshot): List<String> {
        val strategyLine = "Используемая стратегия: ${snapshot.shortTermStrategyType.id}"
        val strategyStateLine = snapshot.state.shortTerm.strategyState?.let { "Состояние стратегии: ${it.strategyType.id}" }
        return listOfNotNull(strategyLine, strategyStateLine) +
            snapshot.state.shortTerm.derivedMessages
                .filterNot { it.role == ChatRole.SYSTEM }
                .map(::formatMessageLine)
    }

    private fun formatManagedNote(note: MemoryNote): String =
        buildString {
            append(note.id)
            append(" [")
            append(noteCategoryTitle(note.category))
            append("]")
            if (note.ownerType == MemoryOwnerType.USER && note.ownerId != null) {
                append(" [user:")
                append(note.ownerId)
                append("]")
            }
            append(": ")
            append(note.content)
        }

    private fun formatMessageLine(message: ChatMessage): String =
        buildString {
            append(messageRoleTitle(message))
            append(": ")
            append(formatMultilineContent(message.content))
        }

    private fun formatMultilineContent(content: String): String {
        val lines = content.lines()
        if (lines.size <= 1) {
            return content
        }

        return buildString {
            append(lines.first())
            lines.drop(1).forEach { line ->
                append("\n  ")
                append(line)
            }
        }
    }

    private fun formatPreviewTokenLines(tokenStats: AgentTokenStats): List<String>? {
        val lines = buildList {
            tokenStats.userPromptTokens?.let { add("  текущее сообщение: $it") }
            tokenStats.historyTokens?.let { add("  история диалога: $it") }
            tokenStats.promptTokensLocal?.let { add("  полный запрос: $it") }
        }
        return lines.ifEmpty { null }
    }

    private fun formatResponseTokenLines(tokenStats: AgentTokenStats): List<String>? {
        val usage = tokenStats.apiUsage ?: return null
        return listOf(
            "Токены ответа:",
            "  запрос: ${usage.promptTokens}",
            "  ответ: ${usage.completionTokens}",
            "  всего: ${usage.totalTokens}"
        )
    }

    private fun layerTitle(layer: MemoryLayer): String =
        when (layer) {
            MemoryLayer.SHORT_TERM -> "краткосрочная"
            MemoryLayer.WORKING -> "рабочая"
            MemoryLayer.LONG_TERM -> "долговременная"
        }

    private fun noteCategoryTitle(category: String): String =
        when (category) {
            "goal" -> "цель"
            "constraint" -> "ограничение"
            "deadline" -> "срок"
            "budget" -> "бюджет"
            "integration" -> "интеграция"
            "decision" -> "решение"
            "open_question" -> "открытый вопрос"
            "communication_style" -> "стиль общения"
            "persistent_preference" -> "постоянное предпочтение"
            "architectural_agreement" -> "архитектурная договорённость"
            "reusable_knowledge" -> "повторно полезное знание"
            else -> category
        }

    private fun messageRoleTitle(message: ChatMessage): String =
        when (message.role) {
            ChatRole.SYSTEM -> "система"
            ChatRole.USER -> "пользователь"
            ChatRole.ASSISTANT -> "ассистент"
        }

    private fun taskStatusLabel(status: TaskStatus): String =
        when (status) {
            TaskStatus.ACTIVE -> "активна"
            TaskStatus.PAUSED -> "на паузе"
            TaskStatus.DONE -> "завершена"
        }

    private fun expectedActionLabel(action: ExpectedAction): String =
        when (action) {
            ExpectedAction.USER_INPUT -> "ожидается ввод пользователя"
            ExpectedAction.AGENT_EXECUTION -> "следующий ход за агентом"
            ExpectedAction.USER_CONFIRMATION -> "ожидается подтверждение пользователя"
            ExpectedAction.NONE -> "не задано"
        }
}
