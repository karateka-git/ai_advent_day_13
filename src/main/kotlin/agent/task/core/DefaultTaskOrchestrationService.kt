package agent.task.core

import agent.task.model.ExpectedAction
import agent.task.model.TaskStage
import agent.task.model.TaskState
import agent.task.model.TaskStatus

/**
 * Базовая policy-реализация, которая превращает task state в typed orchestration-решение.
 */
class DefaultTaskOrchestrationService : TaskOrchestrationService {
    override fun evaluate(taskState: TaskState?): TaskGuardDecision {
        if (taskState == null) {
            return TaskGuardDecision.None
        }

        if (taskState.status == TaskStatus.PAUSED) {
            return TaskGuardDecision.Block(
                reason = TaskBlockReason.PAUSED,
                task = taskState,
                message = "Текущая задача '${taskState.title}' стоит на паузе. Возобнови её через /task resume, если хочешь продолжить этот рабочий трек."
            )
        }

        return when {
            taskState.expectedAction == ExpectedAction.USER_INPUT -> {
                TaskGuardDecision.Guide(
                    task = taskState,
                    mode = behaviorModeFor(taskState.stage),
                    expectedAction = ExpectedAction.USER_INPUT,
                    guidance = "Сейчас для продвижения задачи нужен содержательный ввод пользователя. Не подменяй решение пользователя как уже принятое; лучше явно показать, какого ответа не хватает."
                )
            }

            taskState.stage == TaskStage.PLANNING -> {
                TaskGuardDecision.Guide(
                    task = taskState,
                    mode = TaskBehaviorMode.PLANNING,
                    expectedAction = nonNeutralExpectedAction(taskState.expectedAction),
                    guidance = "Отвечай в режиме planning: проясняй задачу, предлагай варианты и структурируй следующий шаг, не перепрыгивая сразу в полноценную реализацию без явного сигнала."
                )
            }

            taskState.stage == TaskStage.VALIDATION -> {
                TaskGuardDecision.Guide(
                    task = taskState,
                    mode = TaskBehaviorMode.VALIDATION,
                    expectedAction = nonNeutralExpectedAction(taskState.expectedAction),
                    guidance = "Отвечай в режиме validation: проверяй результат, ищи пропуски, риски и недостающие проверки."
                )
            }

            taskState.stage == TaskStage.COMPLETION || taskState.status == TaskStatus.DONE -> {
                TaskGuardDecision.Guide(
                    task = taskState,
                    mode = TaskBehaviorMode.COMPLETION,
                    expectedAction = nonNeutralExpectedAction(taskState.expectedAction),
                    guidance = "Отвечай в режиме completion: подводи итог, оформляй результат и помогай завершить задачу, а не продолжать обычное выполнение."
                )
            }

            taskState.expectedAction == ExpectedAction.USER_CONFIRMATION -> {
                TaskGuardDecision.Guide(
                    task = taskState,
                    mode = behaviorModeFor(taskState.stage),
                    expectedAction = ExpectedAction.USER_CONFIRMATION,
                    guidance = "Сфокусируй ответ на том, что сейчас нужно подтверждение пользователя, и не считай его полученным автоматически."
                )
            }

            taskState.expectedAction == ExpectedAction.AGENT_EXECUTION -> {
                TaskGuardDecision.Guide(
                    task = taskState,
                    mode = behaviorModeFor(taskState.stage),
                    expectedAction = ExpectedAction.AGENT_EXECUTION,
                    guidance = "Можно продолжать рабочий ход со стороны агента, но в рамках текущего stage."
                )
            }

            else -> {
                when (taskState.stage) {
                    TaskStage.EXECUTION -> TaskGuardDecision.None
                    else -> TaskGuardDecision.Guide(
                        task = taskState,
                        mode = behaviorModeFor(taskState.stage),
                        expectedAction = null,
                        guidance = defaultGuidanceFor(taskState.stage)
                    )
                }
            }
        }
    }

    private fun behaviorModeFor(stage: TaskStage): TaskBehaviorMode =
        when (stage) {
            TaskStage.PLANNING -> TaskBehaviorMode.PLANNING
            TaskStage.EXECUTION -> TaskBehaviorMode.EXECUTION
            TaskStage.VALIDATION -> TaskBehaviorMode.VALIDATION
            TaskStage.COMPLETION -> TaskBehaviorMode.COMPLETION
        }

    private fun nonNeutralExpectedAction(expectedAction: ExpectedAction): ExpectedAction? =
        expectedAction.takeUnless { it == ExpectedAction.NONE }

    private fun defaultGuidanceFor(stage: TaskStage): String =
        when (stage) {
            TaskStage.PLANNING ->
                "Отвечай в режиме planning: проясняй задачу, предлагай варианты и структурируй следующий шаг."
            TaskStage.EXECUTION ->
                "Отвечай в режиме execution: продолжай выбранный рабочий ход и предлагай конкретные следующие действия."
            TaskStage.VALIDATION ->
                "Отвечай в режиме validation: проверяй результат и ищи пропуски."
            TaskStage.COMPLETION ->
                "Отвечай в режиме completion: подводи итог и помогай завершить задачу."
        }
}
