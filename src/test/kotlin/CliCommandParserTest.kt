import agent.memory.model.MemoryLayer
import agent.task.model.ExpectedAction
import agent.task.model.TaskStage
import kotlin.test.Test
import kotlin.test.assertEquals
import ui.cli.CliCommand
import ui.cli.CliCommandParser
import ui.cli.InvalidCliCommandReason
import agent.memory.model.ManagedMemoryNoteEdit

class CliCommandParserTest {
    private val parser = CliCommandParser()

    @Test
    fun `parses empty input`() {
        assertEquals(CliCommand.Empty, parser.parse(""))
    }

    @Test
    fun `parses help command`() {
        assertEquals(CliCommand.ShowHelp, parser.parse("/help"))
    }

    @Test
    fun `parses clear command`() {
        assertEquals(CliCommand.Clear, parser.parse("/clear"))
    }

    @Test
    fun `parses models command`() {
        assertEquals(CliCommand.ShowModels, parser.parse("/models"))
    }

    @Test
    fun `parses memory commands`() {
        assertEquals(CliCommand.ShowMemory(null), parser.parse("/memory"))
        assertEquals(CliCommand.ShowMemory(MemoryLayer.SHORT_TERM), parser.parse("/memory short"))
        assertEquals(CliCommand.ShowMemory(MemoryLayer.WORKING), parser.parse("/memory working"))
        assertEquals(CliCommand.ShowMemory(MemoryLayer.LONG_TERM), parser.parse("/memory long"))
        assertEquals(CliCommand.ShowMemoryCategories(null), parser.parse("/memory categories"))
        assertEquals(CliCommand.ShowMemoryCategories(MemoryLayer.WORKING), parser.parse("/memory categories working"))
        assertEquals(CliCommand.ShowPendingMemory, parser.parse("/memory pending"))
        assertEquals(CliCommand.ShowPendingMemoryCommands, parser.parse("/memory pending info"))
        assertEquals(CliCommand.ApprovePendingMemory(emptyList()), parser.parse("/memory approve"))
        assertEquals(CliCommand.RejectPendingMemory(listOf("p1", "p2")), parser.parse("/memory reject p1 p2"))
        assertEquals(
            CliCommand.EditPendingMemory("p3", "text", "Срок - две недели"),
            parser.parse("/memory edit p3 text Срок - две недели")
        )
    }

    @Test
    fun `parses task commands`() {
        assertEquals(CliCommand.ShowTask, parser.parse("/task"))
        assertEquals(CliCommand.ShowTask, parser.parse("/task show"))
        assertEquals(CliCommand.ShowTaskCommands, parser.parse("/task help"))
        assertEquals(
            CliCommand.StartTask("Реализовать task subsystem"),
            parser.parse("/task start Реализовать task subsystem")
        )
        assertEquals(
            CliCommand.UpdateTaskStage(TaskStage.VALIDATION),
            parser.parse("/task stage validation")
        )
        assertEquals(
            CliCommand.UpdateTaskStep("Подготовить CLI-команды"),
            parser.parse("/task step Подготовить CLI-команды")
        )
        assertEquals(
            CliCommand.UpdateTaskExpectedAction(ExpectedAction.USER_CONFIRMATION),
            parser.parse("/task expect user_confirmation")
        )
        assertEquals(CliCommand.PauseTask, parser.parse("/task pause"))
        assertEquals(CliCommand.ResumeTask, parser.parse("/task resume"))
        assertEquals(CliCommand.CompleteTask, parser.parse("/task done"))
        assertEquals(CliCommand.ClearTask, parser.parse("/task clear"))
    }

    @Test
    fun `intercepts incomplete task commands`() {
        assertEquals(
            CliCommand.InvalidCommand(InvalidCliCommandReason.Usage("/task start <title>")),
            parser.parse("/task start")
        )
        assertEquals(
            CliCommand.InvalidCommand(
                InvalidCliCommandReason.Usage("/task stage <planning|execution|validation|completion>")
            ),
            parser.parse("/task stage")
        )
        assertEquals(
            CliCommand.InvalidCommand(
                InvalidCliCommandReason.Usage("/task expect <user_input|agent_execution|user_confirmation|none>")
            ),
            parser.parse("/task expect")
        )
    }

    @Test
    fun `parses direct memory note commands`() {
        assertEquals(
            CliCommand.AddMemoryNote(MemoryLayer.WORKING, "goal", "Собрать ТЗ"),
            parser.parse("/memory add working goal Собрать ТЗ")
        )
        assertEquals(
            CliCommand.EditMemoryNote(
                layer = MemoryLayer.LONG_TERM,
                id = "n7",
                edit = ManagedMemoryNoteEdit.UpdateText("Отвечай кратко")
            ),
            parser.parse("/memory note edit long n7 text Отвечай кратко")
        )
        assertEquals(
            CliCommand.DeleteMemoryNote(MemoryLayer.WORKING, "n2"),
            parser.parse("/memory note delete working n2")
        )
    }

    @Test
    fun `intercepts incomplete memory edit command`() {
        val expected = CliCommand.InvalidCommand(
            InvalidCliCommandReason.Usage("/memory edit <id> text|layer|category <значение>")
        )

        assertEquals(expected, parser.parse("/memory edit"))
        assertEquals(expected, parser.parse("/memory edit p1"))
        assertEquals(expected, parser.parse(" /memory   edit   p1 "))
    }

    @Test
    fun `intercepts memory edit with unsupported field`() {
        assertEquals(
            CliCommand.InvalidCommand(InvalidCliCommandReason.PendingEditUnsupportedField()),
            parser.parse("/memory edit p1 title Новый текст")
        )
    }

    @Test
    fun `parses checkpoint command with optional name`() {
        assertEquals(CliCommand.CreateCheckpoint(null), parser.parse("/checkpoint"))
        assertEquals(
            CliCommand.CreateCheckpoint("v1"),
            parser.parse("/checkpoint v1")
        )
    }

    @Test
    fun `parses branching commands`() {
        assertEquals(CliCommand.ShowBranches, parser.parse("/branches"))
        assertEquals(
            CliCommand.CreateBranch("option-a"),
            parser.parse("/branch create option-a")
        )
        assertEquals(
            CliCommand.SwitchBranch("option-b"),
            parser.parse("/branch use option-b")
        )
    }

    @Test
    fun `intercepts incomplete branch commands`() {
        assertEquals(
            CliCommand.InvalidCommand(
                InvalidCliCommandReason.Usage("/branch create <name> или /branch use <name>")
            ),
            parser.parse("/branch")
        )
        assertEquals(
            CliCommand.InvalidCommand(InvalidCliCommandReason.Usage("/branch create <name>")),
            parser.parse("/branch create")
        )
        assertEquals(
            CliCommand.InvalidCommand(InvalidCliCommandReason.Usage("/branch use <name>")),
            parser.parse("/branch use")
        )
    }

    @Test
    fun `parses use command`() {
        assertEquals(
            CliCommand.SwitchModel("huggingface"),
            parser.parse("/use huggingface")
        )
    }

    @Test
    fun `intercepts incomplete use command`() {
        assertEquals(
            CliCommand.InvalidCommand(InvalidCliCommandReason.Usage("/use <model_id>")),
            parser.parse("/use")
        )
    }

    @Test
    fun `parses exit aliases`() {
        assertEquals(CliCommand.Exit, parser.parse("/exit"))
        assertEquals(CliCommand.Exit, parser.parse("/quit"))
    }

    @Test
    fun `treats plain unknown input as user prompt`() {
        assertEquals(
            CliCommand.UserPrompt("Расскажи историю"),
            parser.parse("Расскажи историю")
        )
    }

    @Test
    fun `treats slash unknown input as cli error`() {
        assertEquals(
            CliCommand.InvalidCommand(InvalidCliCommandReason.UnknownCommand("/memry pending")),
            parser.parse("/memry pending")
        )
    }
}
