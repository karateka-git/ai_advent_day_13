package agent.memory.prompt

import agent.memory.model.LongTermMemory
import agent.memory.model.MemoryNote
import agent.memory.model.MemoryOwnerType
import agent.memory.model.UserAccount
import agent.memory.model.WorkingMemory

/**
 * Собирает memory-вклад в system prompt из semantic views памяти.
 */
class LayeredMemoryPromptAssembler {
    /**
     * Формирует текстовый contribution-блок для system prompt без прямой модификации самого system message.
     *
     * @param activeUser активный пользователь текущей сессии.
     * @param longTermMemory долговременная память со смешанными global- и user-scoped заметками.
     * @param workingMemory рабочая память текущей задачи.
     * @return текстовый contribution для system prompt или `null`, если memory-блоков нет.
     */
    fun assembleContribution(
        activeUser: UserAccount,
        longTermMemory: LongTermMemory,
        workingMemory: WorkingMemory
    ): String? =
        buildLayeredSystemPrompt(
            activeUser = activeUser,
            longTermMemory = longTermMemory,
            workingMemory = workingMemory
        )

    /**
     * Строит итоговый memory contribution с отдельным блоком пользовательского профиля.
     */
    private fun buildLayeredSystemPrompt(
        activeUser: UserAccount,
        longTermMemory: LongTermMemory,
        workingMemory: WorkingMemory
    ): String? {
        val profileNotes = longTermMemory.notes.filter {
            it.ownerType == MemoryOwnerType.USER && it.ownerId == activeUser.id
        }
        val sharedLongTermNotes = longTermMemory.notes.filter { it.ownerType == MemoryOwnerType.GLOBAL }

        val sections = listOfNotNull(
            formatUserProfileSection(activeUser, profileNotes),
            formatNotesSection(
                title = "Long-term memory",
                notes = sharedLongTermNotes
            ),
            formatNotesSection(
                title = "Working memory",
                notes = workingMemory.notes
            )
        )

        if (sections.isEmpty()) {
            return null
        }

        return sections.joinToString(separator = "\n\n")
    }

    /**
     * Форматирует user-scoped long-term заметки активного пользователя в усиленный блок правил.
     */
    private fun formatUserProfileSection(activeUser: UserAccount, notes: List<MemoryNote>): String? {
        if (notes.isEmpty()) {
            return null
        }

        val communicationStyleNotes = notes.filter { it.category == "communication_style" }
        val persistentPreferenceNotes = notes.filter { it.category == "persistent_preference" }
        val otherNotes = notes.filterNot { it.category == "communication_style" || it.category == "persistent_preference" }
        val instructionSections = listOfNotNull(
            formatInstructionSection(
                title = "Правила ответа",
                notes = communicationStyleNotes
            ),
            formatInstructionSection(
                title = "Постоянные предпочтения",
                notes = persistentPreferenceNotes
            ),
            formatInstructionSection(
                title = "Дополнительный контекст пользователя",
                notes = otherNotes
            )
        )

        return buildString {
            appendLine("Профиль пользователя (${activeUser.displayName})")
            appendLine()
            appendLine("Это обязательные правила ответа для текущего пользователя.")
            appendLine("Автоматически применяй их в каждом ответе, если пользователь явно не попросил иначе.")
            appendLine("Если предыдущие сообщения в этой сессии оформлены иначе, всё равно следуй профилю в новом ответе.")
            appendLine()
            appendLine("Приоритет:")
            appendLine("- Текущее сообщение пользователя важнее профиля.")
            appendLine("- Профиль важнее стандартного поведения ассистента.")
            appendLine("- Профиль важнее инерции предыдущих ответов в диалоге.")
            if (instructionSections.isNotEmpty()) {
                appendLine()
                append(instructionSections.joinToString("\n\n"))
            }
        }.trimEnd()
    }

    /**
     * Форматирует обычную секцию памяти для assembled system prompt.
     */
    private fun formatNotesSection(title: String, notes: List<MemoryNote>): String? {
        if (notes.isEmpty()) {
            return null
        }

        return buildString {
            appendLine(title)
            notes.forEach { note ->
                appendLine("- ${note.category}: ${note.content}")
            }
        }.trimEnd()
    }

    /**
     * Форматирует заметки как набор инструкций внутри тематического блока.
     */
    private fun formatInstructionSection(title: String, notes: List<MemoryNote>): String? {
        if (notes.isEmpty()) {
            return null
        }

        return buildString {
            appendLine(title)
            notes.forEach { note ->
                appendLine("- ${note.content}")
            }
        }.trimEnd()
    }
}
