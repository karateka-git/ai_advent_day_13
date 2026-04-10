# Task State Smoke Checklist

Единый smoke-check для task subsystem в проекте `ai_advent_day_13`.

Цели:
- быстро проверять task flow после изменений;
- держать под контролем persistence;
- проверять, что `active user` не ломает task state;
- проверять multitask flow и structured trace без парсинга UI-строк.

## Общие правила

- перед scripted smoke-check всегда делать свежие `.\gradlew.bat build` и `.\gradlew.bat installDist`;
- перед task smoke-check очищать `config/conversations/*` и `config/tasks/*`, чтобы `task-1` и `task-2` создавались предсказуемо;
- перед первым прогоном в серии очищать `build/smoke-check/*`, чтобы в папке оставались только актуальные артефакты;
- `setup` и `verify` запускать строго последовательно;
- ориентироваться на `build/smoke-check/*-trace.jsonl` и `build/smoke-check/*-output.txt`;
- сырой stdout по умолчанию не нужен;
- `DebugTraceListener` считается source of truth для transcript;
- сценарии лежат в `scripts/smoke-check/scenarios/`.

## Актуальные сценарии

- `scripts/smoke-check/scenarios/task-state-setup.txt`
- `scripts/smoke-check/scenarios/task-state-verify.txt`

## Task Smoke

### Setup

Команда:

```powershell
.\scripts\run-scripted-session.ps1 -ScenarioFile .\scripts\smoke-check\scenarios\task-state-setup.txt -OutputFile .\build\smoke-check\task-state-setup-output.txt -ClearConversations -ClearSmokeArtifacts
```

Что проверяем:
- создаются две задачи;
- первая задача уходит в `paused`, когда создаётся вторая;
- `switch task-1` делает первую задачу активной;
- после `switch task-1` обычное сообщение уходит в LLM уже с контекстом первой задачи;
- `resume task-2` возвращает вторую задачу в active;
- после `resume task-2` обычное сообщение уходит в LLM уже с контекстом второй задачи;
- `done task-1` завершает первую задачу, не ломая вторую;
- `/task list` показывает обе задачи и активную пометку;
- persisted session state сохраняется в `config/tasks/`.

### Verify

Команда:

```powershell
.\scripts\run-scripted-session.ps1 -ScenarioFile .\scripts\smoke-check\scenarios\task-state-verify.txt -OutputFile .\build\smoke-check\task-state-verify-output.txt
```

Что проверяем:
- после restart восстанавливаются обе задачи;
- активной остаётся та же задача, что была активна в конце setup;
- обычное сообщение после restart уходит в LLM с контекстом восстановленной активной задачи;
- `switch task-1` для завершённой задачи не делает её активной;
- `/task list` и `/task show` продолжают показывать согласованное состояние;
- structured trace корректно отражает multitask flow.

## Критерий успешности

Task smoke-check считается успешным, если:
- оба scripted-прогона завершаются без ошибок;
- `task-1` и `task-2` переживают restart;
- только одна задача активна;
- завершённая задача не становится активной автоматически;
- после `switch/resume` в `Запрос к модели` и `Контекст задачи` попадает именно текущая активная задача;
- trace/output читаются без зависимости от CLI-рендера.
