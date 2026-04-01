package agent.storage.model

import kotlinx.serialization.Serializable

@Serializable
data class StoredMemoryMetadata(
    val strategyId: String? = null,
    val compressedMessagesCount: Int = 0
)

