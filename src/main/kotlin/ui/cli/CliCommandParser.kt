package ui.cli

import agent.memory.model.MemoryLayer

/**
 * Разбирает строку пользовательского ввода в типизированную CLI-команду.
 */
class CliCommandParser {
    /**
     * Преобразует пользовательский ввод в одну из встроенных команд или обычный prompt.
     */
    fun parse(input: String): CliCommand =
        when {
            input.isEmpty() -> CliCommand.Empty
            input.equals(CliCommands.EXIT, ignoreCase = true) ||
                input.equals(CliCommands.QUIT, ignoreCase = true) -> CliCommand.Exit

            input.equals(CliCommands.CLEAR, ignoreCase = true) -> CliCommand.Clear
            input.equals(CliCommands.MODELS, ignoreCase = true) -> CliCommand.ShowModels
            input.equals(CliCommands.MEMORY, ignoreCase = true) -> CliCommand.ShowMemory(null)
            input.equals("${CliCommands.MEMORY} short", ignoreCase = true) -> CliCommand.ShowMemory(MemoryLayer.SHORT_TERM)
            input.equals("${CliCommands.MEMORY} working", ignoreCase = true) -> CliCommand.ShowMemory(MemoryLayer.WORKING)
            input.equals("${CliCommands.MEMORY} long", ignoreCase = true) -> CliCommand.ShowMemory(MemoryLayer.LONG_TERM)
            input.equals(CliCommands.CHECKPOINT, ignoreCase = true) ->
                CliCommand.CreateCheckpoint(null)
            input.startsWith("${CliCommands.CHECKPOINT} ", ignoreCase = true) ->
                CliCommand.CreateCheckpoint(input.substringAfter(' ').trim())
            input.equals(CliCommands.BRANCHES, ignoreCase = true) ->
                CliCommand.ShowBranches
            input.startsWith("${CliCommands.BRANCH} create ", ignoreCase = true) ->
                CliCommand.CreateBranch(input.substringAfter("${CliCommands.BRANCH} create ").trim())
            input.startsWith("${CliCommands.BRANCH} use ", ignoreCase = true) ->
                CliCommand.SwitchBranch(input.substringAfter("${CliCommands.BRANCH} use ").trim())
            input.startsWith("${CliCommands.USE} ", ignoreCase = true) ->
                CliCommand.SwitchModel(input.substringAfter(' ').trim())

            else -> CliCommand.UserPrompt(input)
        }
}

/**
 * Типизированные команды CLI после разбора пользовательского ввода.
 */
sealed interface CliCommand {
    /**
     * Пустой ввод, который нужно проигнорировать.
     */
    data object Empty : CliCommand

    /**
     * Запрос на завершение сессии.
     */
    data object Exit : CliCommand

    /**
     * Очистка текущего контекста диалога.
     */
    data object Clear : CliCommand

    /**
     * Показ списка доступных моделей.
     */
    data object ShowModels : CliCommand

    /**
     * Показ содержимого одного слоя памяти или всех слоёв сразу.
     */
    data class ShowMemory(
        val layer: MemoryLayer?
    ) : CliCommand

    /**
     * Создание checkpoint активной ветки.
     */
    data class CreateCheckpoint(
        val name: String?
    ) : CliCommand

    /**
     * Показ списка веток и активной ветки.
     */
    data object ShowBranches : CliCommand

    /**
     * Создание новой ветки из последнего checkpoint.
     */
    data class CreateBranch(
        val name: String
    ) : CliCommand

    /**
     * Переключение на существующую ветку.
     */
    data class SwitchBranch(
        val name: String
    ) : CliCommand

    /**
     * Переключение модели по её CLI-идентификатору.
     */
    data class SwitchModel(
        val modelId: String
    ) : CliCommand

    /**
     * Обычное сообщение пользователя, которое нужно отправить агенту.
     */
    data class UserPrompt(
        val value: String
    ) : CliCommand
}
