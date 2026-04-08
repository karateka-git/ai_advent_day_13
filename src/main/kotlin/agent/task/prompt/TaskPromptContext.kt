package agent.task.prompt

/**
 * Представляет вклад task subsystem в итоговый system prompt.
 *
 * @property systemPromptContribution текстовый блок, который task subsystem предлагает включить в
 * итоговый `system message`.
 */
data class TaskPromptContext(
    val systemPromptContribution: String?
)
