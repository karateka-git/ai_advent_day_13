# Scripted Smoke Scenarios

В этой папке лежат versioned-сценарии для scripted smoke-check.

Правило:
- сценарии проверки должны жить в репозитории, чтобы быть доступны разработчику и тестировщику;
- `build/smoke-check/` используется только для артефактов прогонов:
  - текстового вывода;
  - structured trace;
  - временных результатов отдельных запусков.

Базовые сценарии:
- `base.txt`
- `layered.txt`
- `no-compression.txt`
- `summary.txt`
- `sliding-window.txt`
- `sticky-facts.txt`
- `branching.txt`
- `profiles.txt`
- `stepwise-memory.txt`

Сценарии для task subsystem:
- `task-state-setup.txt`
- `task-state-verify.txt`
