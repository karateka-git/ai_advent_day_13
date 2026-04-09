package agent.task.core

import agent.task.model.TaskState

/**
 * Анализирует текущий task state и определяет, как он должен влиять на следующий ответ агента.
 */
interface TaskOrchestrationService {
    /**
     * Возвращает orchestration-решение для следующего ответа агента.
     *
     * @param taskState текущая задача или `null`, если task workflow сейчас отсутствует.
     * @return typed-решение: блокировка, guidance или отсутствие специальных ограничений.
     */
    fun evaluate(taskState: TaskState?): TaskGuardDecision
}
