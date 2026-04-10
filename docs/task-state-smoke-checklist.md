# Task State Smoke Checklist

Ручной smoke-check для stage 1 task subsystem в проекте `ai_advent_day_13`.

Цель:
- быстро проверить `/task`-команды первой версии;
- подтвердить persistence одной conversation-scoped задачи;
- проверить, что переключение `active user` не ломает task state;
- не трогать comparison runner и сценарии стратегий памяти.

Не входит в этот сценарий:
- multitask flow;
- suggestions от агента;
- жёсткая валидация переходов между stage;
- проверка обычного пользовательского сообщения без `/task` через реальную модель.

Примечание:
- сценарий сфокусирован на command flow и persistence;
- инвариант "обычный текст не меняет task state" дополнительно покрыт автотестами.

## Подготовка

1. Если `config/app.properties` отсутствует и нужен только command-only smoke-check, можно временно создать его с фиктивными значениями:

```properties
AGENT_ID=dummy-agent
TIMEWEB_USER_TOKEN=dummy-token
```

2. Очистить runtime-артефакты task smoke-check:

```powershell
Get-ChildItem -LiteralPath .\config\conversations -File -ErrorAction SilentlyContinue | Remove-Item -Force
Get-ChildItem -LiteralPath .\config\tasks -File -ErrorAction SilentlyContinue | Remove-Item -Force
```

3. Собрать и установить свежую версию:

```powershell
.\gradlew.bat build
.\gradlew.bat installDist
```

4. Для scripted-прогона использовать сценарии:

```text
scripts/smoke-check/scenarios/task-state-stage1-setup.txt
scripts/smoke-check/scenarios/task-state-stage1-verify.txt
```

Важно:
- `setup` и `verify` нужно запускать строго последовательно;
- не запускать эти scripted-сценарии параллельно, потому что они используют общий persisted state в `config/conversations/` и `config/tasks/`.

## Полный ручной сценарий этапа 1

1. `/task start Реализовать task subsystem`
2. `/task stage planning`
3. `/task step Продумать доменную модель`
4. `/task expect user_input`
5. `/task show`
6. перезапуск приложения
7. `/task show`
8. `/task pause`
9. обычное сообщение без `/task`
10. убедиться, что paused-задача не возобновилась автоматически
11. `/task resume`
12. `/task stage completion`
13. `/task show`
14. `/task done`
15. `/task show`
16. переключить `active user`
17. проверить, что task state не поменялся автоматически

## Полный ручной сценарий этапа 2

1. `/task start Реализовать task behavior`
2. `/task stage planning`
3. обычное сообщение: `Давай уже писать код`
4. убедиться, что появляется `Контекст задачи` и ответ не игнорирует planning
5. `/task expect user_input`
6. обычное сообщение: `Что дальше?`
7. убедиться, что появляется `Контекст задачи` и агент явно показывает ожидание пользовательского решения
8. `/task pause`
9. обычное сообщение: `Продолжай`
10. убедиться, что paused-задача не продолжается автоматически
11. `/task resume`
12. `/task stage validation`
13. обычное сообщение про проверку результата
14. убедиться, что появляется `Контекст задачи` и ответ выглядит как validation
15. `/task stage completion`
16. обычное сообщение: `Подведи итог`
17. убедиться, что появляется `Контекст задачи` и ответ помогает завершить задачу

## Scripted Run 1: Setup

Команда:

```powershell
.\scripts\run-scripted-session.ps1 -ScenarioFile .\scripts\smoke-check\scenarios\task-state-stage1-setup.txt -OutputFile .\build\smoke-check\task-state-stage1-setup-output.txt -ClearConversations
```

Что проверяем:
- задача создаётся;
- `stage`, `step` и `expected action` меняются через `/task`;
- `pause` работает;
- обычное сообщение без `/task` не возобновляет paused-задачу автоматически;
- `/task show` показывает актуальное состояние;
- после перезапуска задача сохраняется в paused-состоянии.

Ожидаемый результат:
- команды завершаются без падений;
- после `/task pause` в выводе виден `status: paused`;
- после обычного сообщения без `/task` задача всё ещё остаётся paused;
- появляется persisted task state в `config/tasks/`.

## Scripted Run 2: Restore And Complete

Команда:

```powershell
.\scripts\run-scripted-session.ps1 -ScenarioFile .\scripts\smoke-check\scenarios\task-state-stage1-verify.txt -OutputFile .\build\smoke-check\task-state-stage1-verify-output.txt
```

Что проверяем:
- задача восстанавливается после нового запуска;
- `resume` возвращает задачу в активное состояние;
- `completion` используется как stage завершения;
- `done` фиксируется в status;
- переключение `active user` не меняет task state автоматически.

Ожидаемый результат:
- в начале второго прогона `/task show` показывает задачу из первого прогона;
- после `/task resume` задача становится активной;
- после `/task stage completion` в выводе виден stage завершения;
- после `/task done` в выводе виден `status: done`;
- после `/user use reviewer` задача остаётся той же и с тем же state.

## Scripted Run: Stage 2 Behavior

Команда:

```powershell
.\scripts\run-scripted-session.ps1 -ScenarioFile .\scripts\smoke-check\scenarios\task-state-stage2.txt -OutputFile .\build\smoke-check\task-state-stage2-output.txt -ClearConversations
```

Что проверяем:
- при `planning` в trace появляется `Контекст задачи`, а ответ не перескакивает сразу в обычное execution-поведение;
- при `expectedAction = user_input` в trace появляется `Контекст задачи`, и агент явно ждёт пользовательское решение;
- при `paused` модель не вызывается, а задача не продолжается автоматически;
- при `validation` и `completion` в trace появляется `Контекст задачи`, а ответы смещаются в нужный режим.

Ожидаемый результат:
- в output видны блоки `Контекст задачи` для guide-сценариев;
- в paused-сценарии нет `Запрос к модели`, если блокировка сработала;
- scripted output помогает глазами проверить, что stage 2 реально заметен пользователю.

## Scripted Run 1: Stage 3 Multitask Setup

Команда:

```powershell
.\scripts\run-scripted-session.ps1 -ScenarioFile .\scripts\smoke-check\scenarios\task-state-stage3-setup.txt -OutputFile .\build\smoke-check\task-state-stage3-setup-output.txt -ClearConversations
```

Что проверяем:
- `/task start` для второй задачи не стирает первую;
- при создании второй задачи первая активная переводится в паузу;
- `/task show` продолжает показывать только текущую активную задачу;
- persisted task storage уже содержит несколько задач и `activeTaskId`.

Ожидаемый результат:
- в output видно сообщение, что предыдущая активная задача сохранена и переведена в паузу;
- `/task show` показывает вторую задачу как текущую;
- в `config/tasks/` после прогона лежит JSON с двумя задачами и `activeTaskId = task-2`.

## Scripted Run 2: Stage 3 Restore

Команда:

```powershell
.\scripts\run-scripted-session.ps1 -ScenarioFile .\scripts\smoke-check\scenarios\task-state-stage3-verify.txt -OutputFile .\build\smoke-check\task-state-stage3-verify-output.txt
```

Что проверяем:
- после нового запуска активная задача восстанавливается из persisted session state;
- paused-первая задача не становится активной случайно;
- current task UX остаётся минимальным и показывает только активную задачу.

Ожидаемый результат:
- `/task show` после restart показывает вторую задачу;
- в trace/output нет признаков, что первая paused-задача попала в текущий task context;
- persisted JSON по-прежнему содержит обе задачи.

## Что посмотреть после прогона

1. Вывод:

```text
build/smoke-check/task-state-stage1-setup-output.txt
build/smoke-check/task-state-stage1-verify-output.txt
build/smoke-check/task-state-stage2-output.txt
build/smoke-check/task-state-stage3-setup-output.txt
build/smoke-check/task-state-stage3-verify-output.txt
```

Дополнительно:
- `*-output.txt` теперь собирается из structured trace через `DebugTraceListener`, а не парсингом CLI-вывода;
- `*-trace.jsonl` содержит machine-readable trace событий приложения;
- `*-output.txt` собирается из trace в удобочитаемом виде и является основным артефактом для проверки.

2. Persisted task state:

```text
config/tasks/
```

## Критерий успешного smoke-check

Smoke-check считается успешным, если:
- оба scripted-прогона завершаются без ошибок;
- task state переживает повторный запуск;
- переключение `active user` не сбрасывает задачу;
- `completion` и `done` отображаются как разные оси состояния:
  - `completion` как stage;
  - `done` как status.
