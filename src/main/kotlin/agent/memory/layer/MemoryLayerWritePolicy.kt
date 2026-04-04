package agent.memory.layer

import llm.core.model.ChatMessage
import llm.core.model.ChatRole

/**
 * Явно определяет, какие сообщения можно использовать для durable memory allocation.
 */
interface MemoryLayerWritePolicy {
    /**
     * Возвращает `true`, если сообщение можно анализировать и сохранять в working или long-term память.
     *
     * @param message новое сообщение диалога.
     * @return `true`, если allocator должен обработать сообщение.
     */
    fun shouldAllocate(message: ChatMessage): Boolean
}

/**
 * Базовая policy: durable memory формируется только из пользовательских сообщений.
 */
class UserMessageOnlyMemoryLayerWritePolicy : MemoryLayerWritePolicy {
    /**
     * Разрешает запись в durable memory только для сообщений роли `USER`.
     *
     * @param message новое сообщение диалога.
     * @return `true`, если сообщение отправлено пользователем.
     */
    override fun shouldAllocate(message: ChatMessage): Boolean =
        message.role == ChatRole.USER
}
