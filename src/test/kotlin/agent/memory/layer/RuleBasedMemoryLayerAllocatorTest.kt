package agent.memory.layer

import agent.memory.model.LongTermMemory
import agent.memory.model.MemoryNote
import agent.memory.model.MemoryState
import agent.memory.model.WorkingMemory
import kotlin.test.Test
import kotlin.test.assertEquals
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

class RuleBasedMemoryLayerAllocatorTest {
    private val allocator = RuleBasedMemoryLayerAllocator()

    @Test
    fun `stores task details in working memory as separate note fragments`() {
        val allocation = allocator.allocate(
            state = MemoryState(),
            message = ChatMessage(
                role = ChatRole.USER,
                content = "Цель проекта - Telegram-бот. Интеграция только с Google Sheets. Срок две недели."
            )
        )

        assertEquals(
            listOf("goal", "constraint", "deadline", "integration"),
            allocation.workingMemory.notes.map { it.category }
        )
        assertEquals(
            listOf(
                "Цель проекта - Telegram-бот",
                "Интеграция только с Google Sheets",
                "Срок две недели",
                "Интеграция только с Google Sheets"
            ),
            allocation.workingMemory.notes.map { it.content }
        )
    }

    @Test
    fun `does not treat product type mention as integration by itself`() {
        val allocation = allocator.allocate(
            state = MemoryState(),
            message = ChatMessage(
                role = ChatRole.USER,
                content = "MVP Telegram-bot"
            )
        )

        assertEquals(
            listOf("goal"),
            allocation.workingMemory.notes.map { it.category }
        )
    }

    @Test
    fun `stores persistent communication preferences in long-term memory`() {
        val allocation = allocator.allocate(
            state = MemoryState(),
            message = ChatMessage(
                role = ChatRole.USER,
                content = "Отвечай кратко и пиши на русском."
            )
        )

        assertEquals(
            listOf("communication_style"),
            allocation.longTermMemory.notes.map { it.category }.distinct()
        )
        assertEquals(
            listOf("Отвечай кратко и пиши на русском"),
            allocation.longTermMemory.notes.map { it.content }
        )
    }

    @Test
    fun `does not duplicate exact notes`() {
        val initialState = MemoryState(
            longTerm = LongTermMemory(
                notes = listOf(
                    MemoryNote(category = "communication_style", content = "Отвечай кратко")
                )
            )
        )

        val allocation = allocator.allocate(
            state = initialState,
            message = ChatMessage(role = ChatRole.USER, content = "Отвечай кратко.")
        )

        assertEquals(1, allocation.longTermMemory.notes.size)
    }

    @Test
    fun `replaces single-value categories with latest note`() {
        val state = MemoryState(
            working = WorkingMemory(
                notes = listOf(
                    MemoryNote(category = "goal", content = "Сделать MVP")
                )
            ),
            longTerm = LongTermMemory(
                notes = listOf(
                    MemoryNote(category = "communication_style", content = "Отвечай кратко")
                )
            )
        )

        val allocation = allocator.allocate(
            state = state,
            message = ChatMessage(
                role = ChatRole.USER,
                content = "Цель проекта - подготовить production-версию. Отвечай подробно."
            )
        )

        assertEquals(
            listOf(MemoryNote(category = "goal", content = "Цель проекта - подготовить production-версию")),
            allocation.workingMemory.notes.filter { it.category == "goal" }
        )
        assertEquals(
            listOf(MemoryNote(category = "communication_style", content = "Отвечай подробно")),
            allocation.longTermMemory.notes.filter { it.category == "communication_style" }
        )
    }
}
