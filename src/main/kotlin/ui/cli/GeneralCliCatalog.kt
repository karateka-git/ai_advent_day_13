package ui.cli

import app.output.HelpCommandDescriptor
import app.output.HelpCommandGroup

/**
 * Единый каталог основных CLI-команд приложения.
 */
object GeneralCliCatalog {
    val helpGroups: List<HelpCommandGroup> = listOf(
        HelpCommandGroup(
            title = "Общие команды",
            commands = listOf(
                HelpCommandDescriptor(CliCommands.HELP, "Показать общий список команд."),
                HelpCommandDescriptor(CliCommands.MODELS, "Показать доступные модели."),
                HelpCommandDescriptor("${CliCommands.USE} <model_id>", "Переключить модель и выбрать стратегию памяти."),
                HelpCommandDescriptor(CliCommands.CLEAR, "Очистить текущий контекст диалога."),
                HelpCommandDescriptor(CliCommands.EXIT, "Завершить работу."),
                HelpCommandDescriptor(CliCommands.QUIT, "Завершить работу.")
            )
        ),
        HelpCommandGroup(
            title = "Память",
            commands = listOf(
                HelpCommandDescriptor(CliCommands.MEMORY, "Показать все слои памяти."),
                HelpCommandDescriptor("${CliCommands.MEMORY} short", "Показать краткосрочную память."),
                HelpCommandDescriptor("${CliCommands.MEMORY} working", "Показать рабочую память."),
                HelpCommandDescriptor("${CliCommands.MEMORY} long", "Показать долговременную память."),
                HelpCommandDescriptor("${CliCommands.MEMORY} categories", "Показать доступные категории для ручной записи в память."),
                HelpCommandDescriptor("${CliCommands.MEMORY} add <working|long> <category> <текст>", "Добавить запись в рабочую или долговременную память."),
                HelpCommandDescriptor("${CliCommands.MEMORY} note edit <working|long> <id> text|category <значение>", "Изменить сохранённую запись памяти."),
                HelpCommandDescriptor("${CliCommands.MEMORY} note delete <working|long> <id>", "Удалить сохранённую запись памяти."),
                HelpCommandDescriptor(PendingMemoryCliCatalog.SHOW, "Показать pending-кандидаты на сохранение."),
                HelpCommandDescriptor(PendingMemoryCliCatalog.INFO, "Показать справку по командам для pending-памяти.")
            )
        ),
        HelpCommandGroup(
            title = "Ветки и checkpoint",
            commands = listOf(
                HelpCommandDescriptor(CliCommands.CHECKPOINT, "Создать checkpoint активной ветки."),
                HelpCommandDescriptor("${CliCommands.CHECKPOINT} <name>", "Создать именованный checkpoint."),
                HelpCommandDescriptor(CliCommands.BRANCHES, "Показать список веток."),
                HelpCommandDescriptor("${CliCommands.BRANCH} create <name>", "Создать новую ветку от последнего checkpoint."),
                HelpCommandDescriptor("${CliCommands.BRANCH} use <name>", "Переключиться на существующую ветку.")
            )
        )
    )
}
