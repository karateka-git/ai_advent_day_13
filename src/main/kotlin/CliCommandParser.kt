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

