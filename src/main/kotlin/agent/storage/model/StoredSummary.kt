package agent.storage.model

import kotlinx.serialization.Serializable

@Serializable
data class StoredSummary(
    val content: String,
    val coveredMessagesCount: Int
)

