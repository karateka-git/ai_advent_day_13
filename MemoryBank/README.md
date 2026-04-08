# MemoryBank

Локальный банк памяти для правил и договорённостей, которые относятся именно к проекту `ai_advent_day_13`.

## Структура

- `rules/` - проектные устойчивые правила и специальные трактовки команд
- `notes/` - короткие заметки и обзор проекта

## Связь С Общим MemoryBank

- общий банк памяти проекта находится в [C:\Users\compadre\Downloads\Projects\MemoryBank](C:/Users/compadre/Downloads/Projects/MemoryBank)
- при работе над `ai_advent_day_13` нужно учитывать и локальный `MemoryBank`, и общий `MemoryBank`, если в общем банке есть применимые правила или договорённости
- если выполняется синхронизация по локальному `MemoryBank`, её нужно считать полной только после отдельной синхронизации и по общему `MemoryBank`

## Связанные проектные артефакты

- основной ручной smoke-check для стратегий памяти лежит в [docs/memory-strategy-smoke-checklist.md](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_13/docs/memory-strategy-smoke-checklist.md)
- smoke-check для task state лежит в [docs/task-state-smoke-checklist.md](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_13/docs/task-state-smoke-checklist.md)
- versioned scripted-сценарии smoke-check лежат в [scripts/smoke-check/scenarios](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_13/scripts/smoke-check/scenarios)
- `MemoryBank` только ссылается на этот сценарий и задаёт правило, когда его использовать
- практические нюансы scripted smoke-check и проблем кодировки фиксируются в [development-policy.md](/C:/Users/compadre/Downloads/Projects/AiAdvent/day_13/MemoryBank/rules/development-policy.md)
- актуальный smoke-check учитывает pending-confirmation flow и разреженный layered JSON, где пустые `working`, `longTerm` и `pending` секции могут не сериализоваться
- scripted smoke-check больше не должен зависеть от парсинга CLI-строк: source of truth для transcript теперь даёт `DebugTraceListener`


