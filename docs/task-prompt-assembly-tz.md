# Task Prompt Assembly TZ

Этот документ фиксирует точечный рефакторинг prompt assembly для stage 1 task subsystem.

## Цель

Сделать одно место, где реально собирается финальный `system message` для модели, и убрать прямое редактирование `system message` из memory и task подсистем.

## Проблема текущей схемы

До рефакторинга:

- memory subsystem сама модифицировала `system message`;
- task subsystem потом тоже дописывала свой блок;
- финальная сборка prompt была размазана между несколькими слоями;
- `previewTokenStats()` и реальный запрос могли расходиться по форме итогового prompt.

## Целевой контракт

### Memory subsystem

Memory subsystem должна отдавать не готовый финальный prompt, а `MemoryPromptContext`:

- `messages`: история сообщений после short-term стратегии;
- `systemPromptContribution`: текстовый memory contribution для `system message`.

Memory subsystem:

- не знает про task subsystem;
- не редактирует `system message` напрямую;
- не собирает финальный conversation для модели целиком.

### Task subsystem

Task subsystem должна отдавать только task contribution:

- `TaskManager.promptContext()` -> `TaskPromptContext`

Task subsystem:

- не знает про memory subsystem;
- не редактирует `system message` напрямую.

### Agent prompt orchestration

Финальный `system message` должен собираться только через `AgentPromptAssembler`.

`AgentPromptAssembler`:

- принимает базовый `system prompt`;
- принимает список contribution-блоков от подсистем;
- принимает историю сообщений;
- возвращает финальный conversation для модели.

Это единственное место, где реально меняется `system message`.

## Требования к реализации

1. `LayeredMemoryPromptAssembler` должен собирать только memory contribution.
2. `MemoryContextService` должен возвращать `MemoryPromptContext`, а не готовый conversation с уже изменённым `system message`.
3. `TaskManager` должен отдавать task-derived `TaskPromptContext` без прямой модификации `system message`.
4. `MrAgent` не должен вручную склеивать текст `system message`; он должен использовать `AgentPromptAssembler`.
5. `previewTokenStats()` и `ask()` должны проходить через одну и ту же final prompt assembly логику.
6. Если в истории уже есть `system` message, заменяется только первое `system` message.
7. Если `system` message нет, `AgentPromptAssembler` должен добавить его в начало conversation.

## Нефункциональные требования

- memory и task остаются независимыми подсистемами;
- `pending` и durable memory flow не меняются;
- refactor не должен менять user-facing task semantics;
- новый orchestration-код должен быть покрыт KDoc и тестами.

## Критерии готовности

Готово, когда:

- в проекте есть `AgentPromptAssembler` как единая точка финальной сборки prompt;
- memory и task отдают только свои contributions;
- `ask()` и `previewTokenStats()` используют одинаковую схему финальной сборки;
- тесты подтверждают:
  - сборку `system prompt` из нескольких contributions;
  - корректную замену первого `system` message;
  - корректное добавление `system` message при его отсутствии;
  - корректный memory contribution contract;
  - корректный task contribution contract.
