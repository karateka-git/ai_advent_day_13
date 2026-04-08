package ui.cli

import app.output.HelpCommandDescriptor
import app.output.HelpCommandGroup

/**
 * Каталог CLI-команд для управления текущей задачей первого этапа task subsystem.
 */
object TaskCliCatalog {
    val helpGroup: HelpCommandGroup = HelpCommandGroup(
        title = "Задачи",
        commands = listOf(
            HelpCommandDescriptor(CliCommands.TASK, "Показать текущую задачу."),
            HelpCommandDescriptor("${CliCommands.TASK} show", "Показать текущее состояние задачи."),
            HelpCommandDescriptor("${CliCommands.TASK} help", "Показать команды управления задачей."),
            HelpCommandDescriptor("${CliCommands.TASK} start <title>", "Создать текущую задачу."),
            HelpCommandDescriptor(
                "${CliCommands.TASK} stage <planning|execution|validation|completion>",
                "Изменить текущий этап задачи."
            ),
            HelpCommandDescriptor("${CliCommands.TASK} step <text>", "Обновить текущий шаг задачи."),
            HelpCommandDescriptor(
                "${CliCommands.TASK} expect <user_input|agent_execution|user_confirmation|none>",
                "Указать ожидаемое следующее действие."
            ),
            HelpCommandDescriptor("${CliCommands.TASK} pause", "Поставить текущую задачу на паузу."),
            HelpCommandDescriptor("${CliCommands.TASK} resume", "Снять текущую задачу с паузы."),
            HelpCommandDescriptor("${CliCommands.TASK} done", "Пометить текущую задачу как завершённую."),
            HelpCommandDescriptor("${CliCommands.TASK} clear", "Очистить текущую задачу.")
        )
    )
}
