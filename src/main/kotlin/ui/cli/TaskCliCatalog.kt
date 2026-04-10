package ui.cli

import app.output.HelpCommandDescriptor
import app.output.HelpCommandGroup

/**
 * Каталог CLI-команд для управления задачами.
 */
object TaskCliCatalog {
    val helpGroup: HelpCommandGroup = HelpCommandGroup(
        title = "Задачи",
        commands = listOf(
            HelpCommandDescriptor(CliCommands.TASK, "Показать текущую задачу."),
            HelpCommandDescriptor("${CliCommands.TASK} show", "Показать активную задачу."),
            HelpCommandDescriptor("${CliCommands.TASK} list", "Показать все задачи и их статус."),
            HelpCommandDescriptor("${CliCommands.TASK} help", "Показать команды управления задачами."),
            HelpCommandDescriptor("${CliCommands.TASK} start <title>", "Создать новую задачу."),
            HelpCommandDescriptor("${CliCommands.TASK} switch <id>", "Сделать задачу активной."),
            HelpCommandDescriptor("${CliCommands.TASK} resume <id>", "Возобновить задачу по id."),
            HelpCommandDescriptor("${CliCommands.TASK} done <id>", "Завершить задачу по id."),
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
            HelpCommandDescriptor("${CliCommands.TASK} clear", "Очистить текущую задачу.")
        )
    )
}
