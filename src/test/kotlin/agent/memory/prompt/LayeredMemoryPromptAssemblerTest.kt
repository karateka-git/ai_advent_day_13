package agent.memory.prompt

import agent.memory.model.LongTermMemory
import agent.memory.model.MemoryNote
import agent.memory.model.MemoryOwnerType
import agent.memory.model.UserAccount
import agent.memory.model.WorkingMemory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LayeredMemoryPromptAssemblerTest {
    private val assembler = LayeredMemoryPromptAssembler()

    @Test
    fun `builds memory contribution with active user profile and layered notes`() {
        val contribution = assembler.assembleContribution(
            activeUser = UserAccount("anna", "Anna"),
            longTermMemory = LongTermMemory(
                notes = listOf(
                    MemoryNote(
                        id = "",
                        category = "communication_style",
                        content = "Отвечай кратко.",
                        ownerType = MemoryOwnerType.USER,
                        ownerId = "anna"
                    ),
                    MemoryNote(
                        id = "",
                        category = "persistent_preference",
                        content = "Сначала давай вывод, потом детали.",
                        ownerType = MemoryOwnerType.USER,
                        ownerId = "anna"
                    ),
                    MemoryNote("architectural_agreement", "Используй Kotlin")
                )
            ),
            workingMemory = WorkingMemory(
                notes = listOf(MemoryNote("goal", "Собрать ТЗ"))
            )
        )

        assertEquals(
            """
            Профиль пользователя (Anna)

            Это обязательные правила ответа для текущего пользователя.
            Автоматически применяй их в каждом ответе, если пользователь явно не попросил иначе.
            Если предыдущие сообщения в этой сессии оформлены иначе, всё равно следуй профилю в новом ответе.

            Приоритет:
            - Текущее сообщение пользователя важнее профиля.
            - Профиль важнее стандартного поведения ассистента.
            - Профиль важнее инерции предыдущих ответов в диалоге.

            Правила ответа
            - Отвечай кратко.

            Постоянные предпочтения
            - Сначала давай вывод, потом детали.

            Long-term memory
            - architectural_agreement: Используй Kotlin

            Working memory
            - goal: Собрать ТЗ
            """.trimIndent(),
            contribution
        )
    }

    @Test
    fun `does not include profile notes of another user`() {
        val contribution = assembler.assembleContribution(
            activeUser = UserAccount("default", "Default"),
            longTermMemory = LongTermMemory(
                notes = listOf(
                    MemoryNote(
                        id = "",
                        category = "communication_style",
                        content = "Общайся как со старым другом.",
                        ownerType = MemoryOwnerType.USER,
                        ownerId = "vlados"
                    ),
                    MemoryNote(
                        id = "",
                        category = "communication_style",
                        content = "Отвечай строго по делу.",
                        ownerType = MemoryOwnerType.USER,
                        ownerId = "default"
                    )
                )
            ),
            workingMemory = WorkingMemory()
        )

        assertEquals(
            """
            Профиль пользователя (Default)

            Это обязательные правила ответа для текущего пользователя.
            Автоматически применяй их в каждом ответе, если пользователь явно не попросил иначе.
            Если предыдущие сообщения в этой сессии оформлены иначе, всё равно следуй профилю в новом ответе.

            Приоритет:
            - Текущее сообщение пользователя важнее профиля.
            - Профиль важнее стандартного поведения ассистента.
            - Профиль важнее инерции предыдущих ответов в диалоге.

            Правила ответа
            - Отвечай строго по делу.
            """.trimIndent(),
            contribution
        )
    }

    @Test
    fun `returns null when memory contribution is empty`() {
        val contribution = assembler.assembleContribution(
            activeUser = UserAccount("default", "Default"),
            longTermMemory = LongTermMemory(),
            workingMemory = WorkingMemory()
        )

        assertNull(contribution)
    }
}
