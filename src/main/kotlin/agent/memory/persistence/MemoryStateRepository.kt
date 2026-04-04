package agent.memory.persistence

import agent.memory.model.MemoryState
import java.nio.file.Path

/**
 * Абстракция persistence для runtime-состояния памяти.
 */
interface MemoryStateRepository {
    /**
     * Загружает сохранённое состояние памяти из основного хранилища репозитория.
     *
     * @return сохранённый runtime-снимок памяти.
     */
    fun load(): MemoryState

    /**
     * Сохраняет runtime-состояние памяти в основное хранилище репозитория.
     *
     * @param state актуальное состояние памяти.
     */
    fun save(state: MemoryState)

    /**
     * Загружает runtime-состояние памяти из указанного источника импорта.
     *
     * @param sourcePath путь к JSON-файлу с persisted state.
     * @return загруженное runtime-состояние памяти.
     */
    fun loadFrom(sourcePath: Path): MemoryState
}
