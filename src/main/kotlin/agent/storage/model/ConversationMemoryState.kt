package agent.storage.model

import kotlinx.serialization.Serializable

@Serializable
data class ConversationMemoryState(
    val messages: List<StoredMessage> = emptyList(),
    val summaries: List<StoredSummary> = emptyList(),
    val metadata: StoredMemoryMetadata = StoredMemoryMetadata()
)
