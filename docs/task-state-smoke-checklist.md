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
Remove-Item -LiteralPath .\config\conversations\* -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath .\config\tasks\* -Force -ErrorAction SilentlyContinue
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

## Что посмотреть после прогона

1. Вывод:

```text
build/smoke-check/task-state-stage1-setup-output.txt
build/smoke-check/task-state-stage1-verify-output.txt
```

Дополнительно:
- `*-output.txt` теперь собирается из structured trace через `DebugTraceListener`, а не парсингом CLI-вывода;
- `*-trace.jsonl` содержит machine-readable trace событий приложения;
- `*-stdout.txt` сохраняет сырой stdout отдельно и нужен только для отладки проблем запуска или рендера.

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
