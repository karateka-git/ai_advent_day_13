package agent.memory.prompt

import agent.memory.model.MemoryState
/**
 * Собирает memory-вклад в финальный prompt для текущего layered memory state.
 */
interface MemoryContextService {
    /**
     * Строит prompt context для текущего runtime-состояния памяти.
     *
     * Это контекст, который модель увидит без добавления нового пользовательского сообщения.
     *
     * @param state текущее layered memory state.
     * @return сообщения short-term стратегии и memory contribution для system prompt.
     */
    fun effectivePromptContext(state: MemoryState): MemoryPromptContext

    /**
     * Строит preview prompt context для состояния, в которое уже мысленно добавлено следующее
     * пользовательское сообщение.
     *
     * @param state состояние памяти для preview.
     * @return preview-сообщения и memory contribution для system prompt.
     */
    fun previewPromptContext(state: MemoryState): MemoryPromptContext
}

/**
 * Реализация context service на базе активной short-term стратегии и layered prompt assembler.
 */
class DefaultMemoryContextService(
    private val memoryStrategyProvider: () -> agent.memory.core.MemoryStrategy,
    private val promptAssembler: LayeredMemoryPromptAssembler = LayeredMemoryPromptAssembler()
) : MemoryContextService {
    /**
     * Собирает effective memory context из short-term стратегии и отдельного memory contribution.
     *
     * @param state текущее layered memory state.
     * @return сообщения стратегии и memory contribution для system prompt.
     */
    override fun effectivePromptContext(state: MemoryState): MemoryPromptContext =
        MemoryPromptContext(
            messages = memoryStrategyProvider().effectiveContext(state),
            systemPromptContribution = promptAssembler.assembleContribution(
                activeUser = state.activeUser(),
                longTermMemory = state.longTerm,
                workingMemory = state.working
            )
        )

    /**
     * Для preview использует тот же memory contribution, но работает на заранее подготовленном preview state.
     *
     * @param state состояние памяти для preview.
     * @return preview-сообщения и memory contribution для system prompt.
     */
    override fun previewPromptContext(state: MemoryState): MemoryPromptContext =
        effectivePromptContext(state)
}
