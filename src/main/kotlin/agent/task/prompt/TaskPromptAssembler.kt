package agent.task.prompt

import agent.task.model.ExpectedAction
import agent.task.model.TaskState
import agent.task.model.TaskStatus
import agent.task.model.TaskStages

/**
 * Собирает компактный task block для включения в system prompt рядом с memory context.
 */
class TaskPromptAssembler {
    /**
     * Формирует текстовый блок текущей задачи или `null`, если задача не заведена.
     *
     * @param taskState текущее состояние conversation-scoped задачи.
     * @return компактный task block для prompt или `null`.
     */
    fun assemble(taskState: TaskState?): String? {
        if (taskState == null) {
            return null
        }

        val stageDefinition = TaskStages.definitionFor(taskState.stage)
        return buildString {
            appendLine("Task state")
            appendLine("- Title: ${taskState.title}")
            appendLine("- Stage: ${stageDefinition.label}")
            appendLine("- Status: ${statusLabel(taskState.status)}")
            appendLine("- Expected action: ${expectedActionLabel(taskState.expectedAction)}")
            appendLine("- Stage details: ${stageDefinition.description}")
            append("- Current step: ${taskState.currentStep ?: "(not specified)"}")
        }
    }

    /**
     * Добавляет task block к базовому system prompt, если задача существует.
     *
     * @param systemPrompt исходный system prompt.
     * @param taskState текущее состояние задачи.
     * @return system prompt с добавленным task block при наличии задачи.
     */
    fun enrichSystemPrompt(systemPrompt: String, taskState: TaskState?): String {
        val taskBlock = assemble(taskState) ?: return systemPrompt
        return buildString {
            append(systemPrompt.trim())
            append("\n\n")
            append(taskBlock)
        }
    }

    private fun statusLabel(status: TaskStatus): String =
        when (status) {
            TaskStatus.ACTIVE -> "active"
            TaskStatus.PAUSED -> "paused"
            TaskStatus.DONE -> "done"
        }

    private fun expectedActionLabel(action: ExpectedAction): String =
        when (action) {
            ExpectedAction.USER_INPUT -> "user_input"
            ExpectedAction.AGENT_EXECUTION -> "agent_execution"
            ExpectedAction.USER_CONFIRMATION -> "user_confirmation"
            ExpectedAction.NONE -> "none"
        }
}
