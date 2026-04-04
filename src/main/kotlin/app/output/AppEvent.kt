package app.output

import agent.core.AgentInfo
import agent.core.AgentTokenStats
import agent.core.BranchCheckpointInfo
import agent.core.BranchInfo
import agent.core.BranchingStatus
import agent.lifecycle.ContextCompressionStats
import agent.memory.model.MemoryLayer
import agent.memory.model.MemorySnapshot
import agent.memory.strategy.MemoryStrategyOption
import llm.core.model.ChatRole
import llm.core.model.LanguageModelOption

/**
 * Типизированные события application-слоя, которые описывают, что должно быть показано
 * пользователю, не привязывая приложение к конкретному UI.
 */
sealed interface AppEvent {
    data class SessionStarted(
        val modelsCommand: String,
        val useCommand: String
    ) : AppEvent

    data class MemoryStrategySelectionRequested(
        val options: List<MemoryStrategyOption>
    ) : AppEvent

    data class MemoryStrategySelectionPromptRequested(
        val optionsCount: Int
    ) : AppEvent

    data class MemoryStrategySelected(
        val option: MemoryStrategyOption
    ) : AppEvent

    data object MemoryStrategySelectionRejected : AppEvent

    data class AgentInfoAvailable(
        val info: AgentInfo,
        val strategy: MemoryStrategyOption
    ) : AppEvent

    data class ModelsAvailable(
        val options: List<LanguageModelOption>,
        val currentModelId: String
    ) : AppEvent

    data class MemoryStateAvailable(
        val snapshot: MemorySnapshot,
        val selectedLayer: MemoryLayer?
    ) : AppEvent

    data class CheckpointCreated(
        val info: BranchCheckpointInfo
    ) : AppEvent

    data class BranchCreated(
        val info: BranchInfo
    ) : AppEvent

    data class BranchSwitched(
        val info: BranchInfo
    ) : AppEvent

    data class BranchStatusAvailable(
        val status: BranchingStatus
    ) : AppEvent

    data class UserInputPrompt(
        val role: ChatRole
    ) : AppEvent

    data class AssistantResponseAvailable(
        val role: ChatRole,
        val content: String,
        val tokenStats: AgentTokenStats
    ) : AppEvent

    data class TokenPreviewAvailable(
        val tokenStats: AgentTokenStats
    ) : AppEvent

    data object ContextCleared : AppEvent

    data object ModelChanged : AppEvent

    data class ModelSwitchFailed(
        val details: String?
    ) : AppEvent

    data class RequestFailed(
        val details: String?
    ) : AppEvent

    data object SessionFinished : AppEvent

    data object ModelWarmupStarted : AppEvent

    data object ModelWarmupFinished : AppEvent

    data object ModelRequestStarted : AppEvent

    data object ModelRequestFinished : AppEvent

    data object ContextCompressionStarted : AppEvent

    data class ContextCompressionFinished(
        val stats: ContextCompressionStats
    ) : AppEvent
}
