package ui.cli

import app.output.HelpCommandDescriptor

/**
 * Единый каталог CLI-команд для работы с pending-кандидатами памяти.
 */
object PendingMemoryCliCatalog {
    const val SHOW = "/memory pending"
    const val INFO = "/memory pending info"
    const val APPROVE_ALL = "/memory approve"
    const val APPROVE_SELECTED = "/memory approve <id...>"
    const val REJECT_ALL = "/memory reject"
    const val REJECT_SELECTED = "/memory reject <id...>"
    const val EDIT_TEXT = "/memory edit <id> text <новый текст>"
    const val EDIT_LAYER = "/memory edit <id> layer <working|long>"
    const val EDIT_CATEGORY = "/memory edit <id> category <категория>"

    val helpCommands: List<HelpCommandDescriptor> = listOf(
        HelpCommandDescriptor(SHOW, "Показать текущий список pending-кандидатов."),
        HelpCommandDescriptor(INFO, "Показать справку по командам для pending-памяти."),
        HelpCommandDescriptor(APPROVE_ALL, "Подтвердить все pending-кандидаты."),
        HelpCommandDescriptor(APPROVE_SELECTED, "Подтвердить только выбранные pending-кандидаты."),
        HelpCommandDescriptor(REJECT_ALL, "Отклонить все pending-кандидаты."),
        HelpCommandDescriptor(REJECT_SELECTED, "Отклонить только выбранные pending-кандидаты."),
        HelpCommandDescriptor(EDIT_TEXT, "Изменить текст кандидата."),
        HelpCommandDescriptor(EDIT_LAYER, "Изменить целевой слой кандидата."),
        HelpCommandDescriptor(EDIT_CATEGORY, "Изменить категорию кандидата.")
    )
}
