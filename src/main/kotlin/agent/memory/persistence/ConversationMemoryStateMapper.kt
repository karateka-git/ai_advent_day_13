package agent.memory.persistence

import agent.memory.model.MemoryState
import agent.memory.model.MemoryNote
import agent.memory.model.LongTermMemory
import agent.memory.model.ShortTermMemory
import agent.memory.model.WorkingMemory
import agent.storage.mapper.ChatMessageConversationMapper
import agent.storage.model.ConversationMemoryState
import agent.storage.model.StoredLongTermMemory
import agent.storage.model.StoredMemoryNote
import agent.storage.model.StoredShortTermMemory
import agent.storage.model.StoredWorkingMemory

/**
 * Преобразует полное runtime-состояние памяти в persisted JSON-модель и обратно.
 */
class ConversationMemoryStateMapper(
    private val conversationMapper: ChatMessageConversationMapper = ChatMessageConversationMapper(),
    private val strategyStateMapper: StrategyStateMapper = StrategyStateMapper(conversationMapper)
) {
    /**
     * Преобразует persisted state в runtime state.
     */
    fun toRuntime(storedState: ConversationMemoryState): MemoryState =
        MemoryState(
            shortTerm = ShortTermMemory(
                messages = storedState.shortTerm.messages.map(conversationMapper::fromStoredMessage),
                strategyState = strategyStateMapper.toRuntime(storedState.shortTerm.strategyState)
            ),
            working = WorkingMemory(
                notes = storedState.working.notes.map(::toRuntimeNote)
            ),
            longTerm = LongTermMemory(
                notes = storedState.longTerm.notes.map(::toRuntimeNote)
            )
        )

    /**
     * Преобразует runtime state в persisted state.
     */
    fun toStored(runtimeState: MemoryState): ConversationMemoryState =
        ConversationMemoryState(
            shortTerm = StoredShortTermMemory(
                messages = runtimeState.shortTerm.messages.map(conversationMapper::toStoredMessage),
                strategyState = strategyStateMapper.toStored(runtimeState.shortTerm.strategyState)
            ),
            working = StoredWorkingMemory(
                notes = runtimeState.working.notes.map(::toStoredNote)
            ),
            longTerm = StoredLongTermMemory(
                notes = runtimeState.longTerm.notes.map(::toStoredNote)
            )
        )

    private fun toRuntimeNote(note: StoredMemoryNote): MemoryNote =
        MemoryNote(
            category = note.category,
            content = note.content
        )

    private fun toStoredNote(note: MemoryNote): StoredMemoryNote =
        StoredMemoryNote(
            category = note.category,
            content = note.content
        )
}
