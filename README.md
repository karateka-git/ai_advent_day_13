# day_13 — Явная память и формализованное состояние задач

`day_13` вырос из [day_12](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_12), где уже были:

- layered memory model;
- short-term стратегии памяти;
- multi-user персонализация;
- ручное управление памятью через CLI.

На текущем этапе поверх memory subsystem добавлена отдельная **task subsystem** с формализованным состоянием задачи. Теперь агент опирается не только на память, но и на явную модель рабочей задачи:

- этап задачи;
- текущий шаг;
- ожидаемое действие;
- статус задачи;
- multitask session с одной активной задачей.

Итог: проект стал ближе к ассистенту, у которого есть и **управляемая память**, и **явное рабочее состояние задачи**.

## Что умеет проект сейчас

### Memory subsystem

- layered memory:
  - `short-term`
  - `working`
  - `long-term`
  - `pending`
- short-term стратегии памяти;
- выделение кандидатов в durable memory;
- pending-flow с подтверждением;
- ручное редактирование памяти;
- multi-user профили и `active user`.

### Task subsystem

- формализованное состояние задачи:
  - `stage`
  - `currentStep`
  - `expectedAction`
  - `status`
- pause / resume / done;
- persistence задач между перезапусками;
- несколько задач в одной сессии;
- одна активная задача в каждый момент времени;
- только активная задача влияет на prompt и orchestration.

### Prompt / Orchestration

- memory и tasks собираются как отдельные prompt contributions;
- финальный `system message` формируется в одном месте;
- агент учитывает `task state` при выборе режима ответа;
- при `paused` задача не продолжается автоматически;
- при смене активной задачи в prompt уходит именно она.

### Smoke / Debug

- scripted smoke-check для memory и task flow;
- structured trace через `DebugTraceListener`;
- transcript собирается из trace, а не из парсинга CLI-строк.

## Как устроена memory subsystem

### Short-term

`short-term` хранит:

- raw log диалога;
- derived short-term view;
- strategy-specific state.

Raw log остаётся источником истины, а derived view зависит от выбранной стратегии.

### Working

`working` хранит оперативный контекст:

- текущие цели;
- ограничения;
- решения;
- открытые вопросы;
- данные текущей работы.

### Long-term

`long-term` хранит устойчивые знания:

- предпочтения;
- договорённости;
- повторно полезные решения;
- проектные правила;
- user-scoped заметки профиля.

### Pending

`pending` хранит кандидатов на сохранение, которые ещё не подтверждены пользователем.

## Как изменился memory pipeline

Смысл блока остался прежним: память больше не является просто “историей диалога”. Теперь это pipeline из отдельных шагов и подсистем.

Текущий high-level flow такой:

1. CLI определяет, это команда или обычное сообщение.
2. Если это команда, она обрабатывается без запроса к основной модели.
3. Если это обычное сообщение, оно попадает в `short-term`.
4. Memory manager извлекает кандидатов для durable memory.
5. Кандидаты валидируются и делятся на auto-apply и `pending`.
6. Обновляются memory layers.
7. Memory subsystem отдаёт:
   - effective messages;
   - свой prompt contribution.
8. Task subsystem отдельно отдаёт task prompt context для активной задачи.
9. Финальный `system message` собирается в одном orchestration-слое.
10. Модель получает итоговый prompt и отвечает уже с учётом memory + task state.

Ключевая идея: ни memory subsystem, ни task subsystem не правят final prompt сами. Они отдают свои данные, а финальная сборка делается централизованно.

## Как устроена task subsystem

У каждой задачи есть:

- `stage` — текущий этап работы;
- `currentStep` — текущий конкретный шаг;
- `expectedAction` — какое действие нужно для продвижения;
- `status` — жизненное состояние задачи.

Базовые этапы:

- `planning`
- `execution`
- `validation`
- `completion`

Базовые статусы:

- `active`
- `paused`
- `done`

Дополнительно task subsystem хранит `TaskSessionState`:

- список задач;
- `activeTaskId`;
- правило “несколько задач, одна активная”.

Если создаётся новая задача, предыдущая активная уходит в `paused`.  
Завершённая задача не может стать активной снова через обычный `switch`.

## Команды задач

Сейчас доступны:

- `/task show`
- `/task list`
- `/task start <title>`
- `/task switch <id>`
- `/task resume <id>`
- `/task done <id>`
- `/task pause`
- `/task stage <planning|execution|validation|completion>`
- `/task step <text>`
- `/task expect <user_input|agent_execution|user_confirmation|none>`

Минимальная модель использования такая:

1. создать задачу;
2. зафиксировать этап и текущий шаг;
3. при необходимости поставить на паузу;
4. переключиться на другую задачу;
5. позже вернуться через `switch` или `resume`;
6. завершить через `done`.

## Как task state влияет на ответы

Агент использует task state в двух формах:

1. как prompt context;
2. как orchestration policy.

Это даёт следующий эффект:

- `paused` блокирует продолжение рабочего трека;
- `expectedAction` подсказывает, чего сейчас не хватает;
- `stage` задаёт режим ответа;
- история диалога используется как фон, но не как главный источник истины о текущей задаче.

Только активная задача влияет на prompt и orchestration. Остальные задачи хранятся в session state, но не подмешиваются в ответ напрямую.

## Smoke-check

Актуальные smoke-check документы:

- [memory-strategy-smoke-checklist.md](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_13/docs/memory-strategy-smoke-checklist.md)
- [task-state-smoke-checklist.md](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_13/docs/task-state-smoke-checklist.md)

Актуальные task smoke-сценарии:

- [task-state-setup.txt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_13/scripts/smoke-check/scenarios/task-state-setup.txt)
- [task-state-verify.txt](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_13/scripts/smoke-check/scenarios/task-state-verify.txt)

Правила smoke-check:

- `setup -> verify` запускать строго последовательно;
- перед первой серией очищать `build/smoke-check/*`;
- для task smoke дополнительно очищать `config/tasks/*`;
- source of truth для transcript — structured trace, который пишет `DebugTraceListener`.

## План развития task manager

Текущее состояние уже покрывает базовый рабочий flow, но дальше task subsystem можно развивать в нескольких направлениях:

- сделать UX списка задач более удобным и менее “техническим”;
- добавить user-facing сводки при `switch/resume`;
- добавить agent suggestions для перевода задачи между этапами;
- аккуратно усилить помощь агенту при создании новой задачи из обычного диалога;
- вынести некоторые debug и token-counting детали выше memory manager;
- расширить smoke-check новыми сценариями по мере роста task UX.

## Что в итоге получилось

Сейчас проект — это:

- агент с layered memory model;
- отдельная управляемая task subsystem;
- multitask session с одной активной задачей;
- централизованная сборка финального prompt;
- multi-user персонализация;
- scripted smoke-check с structured trace.

То есть это уже не просто CLI-чат с памятью, а ассистент с явной памятью, персонализацией и формализованным состоянием рабочей задачи.
