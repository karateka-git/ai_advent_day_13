# System Message Source Of Truth TZ

Этот документ фиксирует следующий шаг рефакторинга prompt assembly после введения `AgentPromptAssembler`.

## Цель

Сделать `AgentPromptAssembler` не только единственным местом редактирования итогового `system message`, но и единственным местом его создания.

## Проблема

Сейчас в проекте одновременно существуют два источника базового system prompt:

- `baseSystemPrompt`, который передаётся в `AgentPromptAssembler`;
- `system`-сообщение, которое memory subsystem хранит в `shortTerm.rawMessages`.

Из-за этого:

- `MemoryPromptContext.messages` уже может содержать `system`-сообщение;
- финальная сборка всё равно пересобирает `system message` отдельно;
- в модели остаётся дублирование ответственности между memory subsystem и orchestration-слоем.

## Целевой инвариант

- `shortTerm.rawMessages` хранит только runtime-сообщения диалога;
- memory subsystem не хранит базовый system prompt в short-term history;
- `MemoryPromptContext.messages` не содержит placeholder system prompt;
- `AgentPromptAssembler` всегда сам добавляет финальный `system message` в начало conversation;
- memory strategies могут при необходимости добавлять synthetic system-role сообщения как derived context, но не базовый system prompt агента.

## Требования к реализации

1. `DefaultMemoryManager` больше не должен создавать initial short-term state с system message.
2. `clear()` не должен восстанавливать system message в `shortTerm.rawMessages`.
3. `MemoryClearPolicy` должен перестать требовать `systemMessage` как аргумент.
4. `AgentPromptAssembler` должен всегда prepend'ить финальный `system message`, а не заменять первое `system`-сообщение в истории.
5. Short-term strategies должны перестать полагаться на наличие базового `system`-сообщения в `rawMessages`.
6. При загрузке persisted memory state нужно привести `rawMessages` к новому инварианту без базового system prompt.
7. `DefaultMemoryManager` должен перестать использовать stored `system` message как часть short-term state lifecycle.

## Нефункциональные требования

- task subsystem и memory subsystem остаются разделёнными;
- финальный `system message` по-прежнему собирается только через `AgentPromptAssembler`;
- preview и runtime используют одинаковую схему финальной сборки;
- новый инвариант должен быть отражён в тестах.

## Критерии готовности

Готово, когда:

- `currentConversation()` после пустого старта не содержит базовый system prompt;
- `clear()` очищает short-term до пустого runtime-журнала, а не до одного system message;
- `AgentPromptAssembler` всегда добавляет финальный `system message` сам;
- memory prompt contract и task prompt contract продолжают работать;
- полный `.\gradlew.bat test` проходит.
