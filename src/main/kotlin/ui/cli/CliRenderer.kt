package ui.cli

import app.output.AppEvent
import app.output.AppEventSink
import agent.memory.strategy.MemoryStrategyOption

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

