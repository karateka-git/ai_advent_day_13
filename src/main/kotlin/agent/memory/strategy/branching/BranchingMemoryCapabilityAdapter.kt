package agent.memory.strategy.branching

import agent.core.BranchCheckpointInfo
import agent.core.BranchInfo
import agent.core.BranchingStatus
import agent.memory.model.MemoryState

/**
 * Адаптирует branching-операции к lifecycle менеджера памяти.
 *
 * @param branchCoordinator доменный сервис для работы с checkpoint и ветками.
 * @param enabled признак доступности branching capability для активной стратегии.
 * @param stateProvider поставщик текущего состояния памяти.
 * @param stateUpdater функция обновления runtime-состояния памяти.
 * @param persistState функция сохранения актуального состояния в storage.
 */
class BranchingMemoryCapabilityAdapter(
    private val branchCoordinator: BranchCoordinator,
    private val enabled: () -> Boolean,
    private val stateProvider: () -> MemoryState,
    private val stateUpdater: (MemoryState) -> Unit,
    private val persistState: () -> Unit
) : BranchingCapability {
    /**
     * Создаёт checkpoint из текущей активной ветки и сохраняет обновлённое состояние.
     *
     * @param name необязательное имя checkpoint.
     * @return информация о созданном checkpoint.
     */
    override fun createCheckpoint(name: String?): BranchCheckpointInfo {
        requireBranchingEnabled()
        val result = branchCoordinator.createCheckpoint(stateProvider(), name)
        stateUpdater(result.state)
        persistState()
        return result.info
    }

    /**
     * Создаёт новую ветку из последнего checkpoint и сохраняет обновлённое состояние.
     *
     * @param name имя новой ветки.
     * @return информация о созданной ветке.
     */
    override fun createBranch(name: String): BranchInfo {
        requireBranchingEnabled()
        val result = branchCoordinator.createBranch(stateProvider(), name)
        stateUpdater(result.state)
        persistState()
        return result.info
    }

    /**
     * Переключает активную ветку и сохраняет обновлённое состояние.
     *
     * @param name имя ветки для переключения.
     * @return информация об активированной ветке.
     */
    override fun switchBranch(name: String): BranchInfo {
        requireBranchingEnabled()
        val result = branchCoordinator.switchBranch(stateProvider(), name)
        stateUpdater(result.state)
        persistState()
        return result.info
    }

    /**
     * Возвращает актуальный статус branching-модели.
     *
     * @return состояние ветвления для текущей памяти.
     */
    override fun branchStatus(): BranchingStatus {
        requireBranchingEnabled()
        return branchCoordinator.branchStatus(stateProvider())
    }

    private fun requireBranchingEnabled() {
        require(enabled()) {
            "Команды ветвления доступны только для стратегии Branching."
        }
    }
}
