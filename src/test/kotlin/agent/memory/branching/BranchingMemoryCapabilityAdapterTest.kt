package agent.memory.branching

import agent.memory.model.BranchConversationState
import agent.memory.model.BranchingStrategyState
import agent.memory.model.MemoryState
import agent.memory.model.ShortTermMemory
import agent.memory.strategy.branching.BranchCoordinator
import agent.memory.strategy.branching.BranchingMemoryCapabilityAdapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

class BranchingMemoryCapabilityAdapterTest {
    @Test
    fun `creates checkpoint and persists updated state`() {
        var state = initialState()
        var persistCalls = 0
        val capability = BranchingMemoryCapabilityAdapter(
            branchCoordinator = BranchCoordinator(),
            enabled = { true },
            stateProvider = { state },
            stateUpdater = { updatedState -> state = updatedState },
            persistState = { persistCalls++ }
        )

        val checkpoint = capability.createCheckpoint("base")

        assertEquals("base", checkpoint.name)
        assertEquals(1, persistCalls)
        val branchingState = state.shortTerm.strategyState as BranchingStrategyState
        assertEquals("base", branchingState.latestCheckpointName)
        assertEquals(1, branchingState.checkpoints.size)
    }

    @Test
    fun `switches branch using updated runtime state`() {
        var state = initialState()
        val capability = BranchingMemoryCapabilityAdapter(
            branchCoordinator = BranchCoordinator(),
            enabled = { true },
            stateProvider = { state },
            stateUpdater = { updatedState -> state = updatedState },
            persistState = {}
        )

        capability.createCheckpoint("base")
        capability.createBranch("option-a")
        val branchingState = state.shortTerm.strategyState as BranchingStrategyState
        state = state.copy(
            shortTerm = state.shortTerm.copy(
                strategyState = branchingState.copy(
                    branches = branchingState.branches.map { branch ->
                        if (branch.name == "option-a") {
                            branch.copy(messages = branch.messages + ChatMessage(ChatRole.USER, "branch-u1"))
                        } else {
                            branch
                        }
                    }
                )
            )
        )

        val switchedBranch = capability.switchBranch("option-a")

        assertEquals("option-a", switchedBranch.name)
        assertEquals("option-a", (state.shortTerm.strategyState as BranchingStrategyState).activeBranchName)
        assertEquals(
            listOf(
                ChatMessage(ChatRole.SYSTEM, "system"),
                ChatMessage(ChatRole.USER, "u1"),
                ChatMessage(ChatRole.ASSISTANT, "a1"),
                ChatMessage(ChatRole.USER, "branch-u1")
            ),
            state.shortTerm.messages
        )
    }

    @Test
    fun `fails fast when branching capability is disabled`() {
        val capability = BranchingMemoryCapabilityAdapter(
            branchCoordinator = BranchCoordinator(),
            enabled = { false },
            stateProvider = { initialState() },
            stateUpdater = {},
            persistState = {}
        )

        val error = assertFailsWith<IllegalArgumentException> {
            capability.branchStatus()
        }

        assertEquals("Команды ветвления доступны только для стратегии Branching.", error.message)
    }

    private fun initialState(): MemoryState =
        MemoryState(
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
}
