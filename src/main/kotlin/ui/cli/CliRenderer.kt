package ui.cli

import agent.memory.model.MemoryLayer
import agent.memory.model.MemorySnapshot
import agent.memory.model.MemoryState
import agent.memory.strategy.MemoryStrategyOption
import app.output.AppEvent
import app.output.AppEventSink
import llm.core.model.ChatMessage

/**
 * CLI-реализация presentation-слоя, которая отвечает за весь пользовательский вывод.
 */
class CliRenderer(
    private val loadingIndicator: LoadingIndicator = LoadingIndicator(),
    private val tokenStatsFormatter: ConsoleTokenStatsFormatter = ConsoleTokenStatsFormatter()
) : AppEventSink {
    override fun emit(event: AppEvent) {
        when (event) {
            is AppEvent.SessionStarted -> {
                println("Чат готов. Введите 'exit' или 'quit', чтобы завершить работу.")
                println(
                    "Для просмотра моделей введите '${event.modelsCommand}'. " +
                        "Для переключения модели введите '${event.useCommand} <id>'."
                )
            }

            is AppEvent.MemoryStrategySelectionRequested -> {
                println("Выберите стратегию памяти для нового агента:")
                event.options.forEachIndexed { index, option ->
                    println("${index + 1}. ${option.displayName} - ${option.description}")
                }
            }

            is AppEvent.MemoryStrategySelectionPromptRequested -> {
                print("Введите номер стратегии [1-${event.optionsCount}]: ")
            }

            is AppEvent.MemoryStrategySelected -> {
                println("Выбрана стратегия: ${event.option.displayName}")
                println("Как работает: ${event.option.description}")
                printAdditionalCommands(
                    event.option,
                    header = "Дополнительные команды:"
                )
            }

            AppEvent.MemoryStrategySelectionRejected -> {
                println("Некорректный выбор. Попробуйте ещё раз.")
            }

            is AppEvent.AgentInfoAvailable -> {
                println("Агент: ${event.info.name}")
                println("Описание: ${event.info.description}")
                println("Модель: ${event.info.model}")
                println("Стратегия памяти: ${event.strategy.displayName}")
                println("Поведение стратегии: ${event.strategy.description}")
                printAdditionalCommands(
                    event.strategy,
                    header = "Команды стратегии:"
                )
            }

            is AppEvent.ModelsAvailable -> {
                println(
                    buildString {
                        appendLine("Доступные модели:")
                        event.options.forEach { option ->
                            val marker = if (option.id == event.currentModelId) "*" else " "
                            append(marker)
                            append(" ")
                            append(option.id)
                            append(" - ")
                            append(option.displayName)
                            if (!option.isConfigured) {
                                append(" (недоступна: ${option.unavailableReason})")
                            }
                            appendLine()
                        }
                    }.trimEnd()
                )
            }

            is AppEvent.MemoryStateAvailable -> {
                renderMemoryState(
                    snapshot = event.snapshot,
                    selectedLayer = event.selectedLayer
                )
            }

            is AppEvent.CheckpointCreated -> {
                println("Создан checkpoint: ${event.info.name}")
                println("Исходная ветка: ${event.info.sourceBranchName}")
            }

            is AppEvent.BranchCreated -> {
                println("Создана ветка: ${event.info.name}")
                println("Источник: ${event.info.sourceCheckpointName ?: "без checkpoint"}")
            }

            is AppEvent.BranchSwitched -> {
                println("Активная ветка: ${event.info.name}")
            }

            is AppEvent.BranchStatusAvailable -> {
                println("Активная ветка: ${event.status.activeBranchName}")
                println("Последний checkpoint: ${event.status.latestCheckpointName ?: "нет"}")
                println("Ветки:")
                event.status.branches.forEach { branch ->
                    val marker = if (branch.isActive) "*" else " "
                    println("$marker ${branch.name} (checkpoint: ${branch.sourceCheckpointName ?: "нет"})")
                }
            }

            is AppEvent.UserInputPrompt -> {
                print("${event.role.displayName}: ")
            }

            is AppEvent.AssistantResponseAvailable -> {
                println()
                println("${event.role.displayName}: ${event.content}")
                tokenStatsFormatter.formatResponse(event.tokenStats)?.let {
                    println()
                    println(it)
                }
                println()
            }

            is AppEvent.TokenPreviewAvailable -> {
                tokenStatsFormatter.formatPreview(event.tokenStats)?.let { preview ->
                    println()
                    println(preview)
                    println()
                }
            }

            AppEvent.ContextCleared -> println("Контекст очищен. Системное сообщение сохранено.")
            AppEvent.ModelChanged -> println("Текущая модель изменена.")
            is AppEvent.ModelSwitchFailed -> println("Не удалось переключить модель: ${event.details}")
            is AppEvent.RequestFailed -> println("Не удалось выполнить запрос: ${event.details}")
            AppEvent.SessionFinished -> println("Чат завершён.")
            AppEvent.ModelWarmupStarted -> loadingIndicator.start("Подготовка модели")
            AppEvent.ModelWarmupFinished -> loadingIndicator.stop()
            AppEvent.ModelRequestStarted -> loadingIndicator.start("Ассистент думает")
            AppEvent.ModelRequestFinished -> loadingIndicator.stop()
            AppEvent.ContextCompressionStarted -> loadingIndicator.start("Сжимаем контекст")

            is AppEvent.ContextCompressionFinished -> {
                loadingIndicator.stop()

                val message =
                    if (
                        event.stats.tokensBefore != null &&
                        event.stats.tokensAfter != null &&
                        event.stats.savedTokens != null
                    ) {
                        "Контекст сжат: ${event.stats.tokensBefore} -> ${event.stats.tokensAfter} токенов, " +
                            "экономия ${event.stats.savedTokens}"
                    } else {
                        "Контекст сжат."
                    }

                println(message)
            }
        }
    }

    private fun renderMemoryState(snapshot: MemorySnapshot, selectedLayer: MemoryLayer?) {
        memoryLayers(selectedLayer).forEach { layer ->
            println("Уровень памяти: ${layerTitle(layer)}")
            val lines = linesForLayer(snapshot, layer)
            if (lines.isEmpty()) {
                println("(пусто)")
            } else {
                lines.forEach { line -> println("- $line") }
            }
        }
    }

    private fun memoryLayers(selectedLayer: MemoryLayer?): List<MemoryLayer> =
        selectedLayer?.let(::listOf)
            ?: listOf(MemoryLayer.SHORT_TERM, MemoryLayer.WORKING, MemoryLayer.LONG_TERM)

    private fun layerTitle(layer: MemoryLayer): String =
        when (layer) {
            MemoryLayer.SHORT_TERM -> "краткосрочная"
            MemoryLayer.WORKING -> "рабочая"
            MemoryLayer.LONG_TERM -> "долговременная"
        }

    private fun linesForLayer(snapshot: MemorySnapshot, layer: MemoryLayer): List<String> =
        when (layer) {
            MemoryLayer.SHORT_TERM -> buildShortTermLines(snapshot)
            MemoryLayer.WORKING -> snapshot.state.working.notes.map { "${noteCategoryTitle(it.category)}: ${it.content}" }
            MemoryLayer.LONG_TERM -> snapshot.state.longTerm.notes.map { "${noteCategoryTitle(it.category)}: ${it.content}" }
        }

    private fun buildShortTermLines(snapshot: MemorySnapshot): List<String> {
        val strategyLine = "стратегия: ${snapshot.shortTermStrategyType.id}"
        val strategyStateLine = snapshot.state.shortTerm.strategyState?.let { "состояние стратегии: ${it.strategyType.id}" }
        val messages = snapshot.state.shortTerm.messages.map(::formatMessageLine)
        return listOfNotNull(strategyLine, strategyStateLine) + messages
    }

    private fun formatMessageLine(message: ChatMessage): String =
        "${messageRoleTitle(message)}: ${message.content}"

    private fun messageRoleTitle(message: ChatMessage): String =
        when (message.role) {
            llm.core.model.ChatRole.SYSTEM -> "система"
            llm.core.model.ChatRole.USER -> "пользователь"
            llm.core.model.ChatRole.ASSISTANT -> "ассистент"
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

    private fun printAdditionalCommands(
        option: MemoryStrategyOption,
        header: String = "Дополнительные команды:"
    ) {
        if (option.additionalCommands.isEmpty()) {
            return
        }

        println(header)
        option.additionalCommands.forEach { command ->
            println("- ${command.command} - ${command.description}")
        }
    }
}
