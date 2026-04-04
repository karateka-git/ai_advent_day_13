package agent.memory.strategy.branching

import agent.memory.model.BranchConversationState
import agent.memory.model.BranchingStrategyState
import agent.memory.model.MemoryState
import agent.memory.model.ShortTermMemory
import kotlin.test.Test
import kotlin.test.assertEquals
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

class BranchCoordinatorTest {
    private val coordinator = BranchCoordinator()

    @Test
    fun `creates checkpoint and branch then switches to it`() {
        val initialState = MemoryState(
            shortTerm = ShortTermMemory(
                messages = listOf(
                    ChatMessage(ChatRole.SYSTEM, "system"),
                    ChatMessage(ChatRole.USER, "u1"),
                    ChatMessage(ChatRole.ASSISTANT, "a1")
                ),
                strategyState = BranchingStrategyState(
                    branches = listOf(
                        BranchConversationState(
                            name = "main",
                            messages = listOf(
                                ChatMessage(ChatRole.SYSTEM, "system"),
                                ChatMessage(ChatRole.USER, "u1"),
                                ChatMessage(ChatRole.ASSISTANT, "a1")
                            )
                        )
                    )
                )
            )
        )

        val checkpointResult = coordinator.createCheckpoint(initialState, "base")
        assertEquals("base", checkpointResult.info.name)

        val branchResult = coordinator.createBranch(checkpointResult.state, "option-a")
        assertEquals("option-a", branchResult.info.name)

        val branchingState = branchResult.state.shortTerm.strategyState as BranchingStrategyState
        val switchedState = branchResult.state.copy(
            shortTerm = branchResult.state.shortTerm.copy(
                strategyState = branchingState.copy(
                    branches = branchingState.branches.map { branch ->
                        if (branch.name == "option-a") {
                            branch.copy(
                                messages = branch.messages + ChatMessage(ChatRole.USER, "branch-u1")
                            )
                        } else {
                            branch
                        }
                    }
                )
            )
        )

        val switchResult = coordinator.switchBranch(switchedState, "option-a")

        assertEquals("option-a", switchResult.info.name)
        assertEquals(
            listOf(
                ChatMessage(ChatRole.SYSTEM, "system"),
                ChatMessage(ChatRole.USER, "u1"),
                ChatMessage(ChatRole.ASSISTANT, "a1"),
                ChatMessage(ChatRole.USER, "branch-u1")
            ),
            switchResult.state.shortTerm.messages
        )
    }
}
