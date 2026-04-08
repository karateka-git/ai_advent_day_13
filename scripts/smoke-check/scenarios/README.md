# Scripted Smoke Scenarios

В этой папке лежат versioned-сценарии для scripted smoke-check.

Правило:
- сценарии проверки должны жить в репозитории, чтобы быть доступны разработчику и тестировщику;
- `build/smoke-check/` используется только для артефактов прогонов:
  - текстового вывода;
  - JSON-снимков состояния;
  - временных результатов отдельных запусков.

Базовые сценарии:
- `base.txt`
- `layered.txt`
- `no-compression.txt`
- `summary.txt`
- `sliding-window.txt`
- `sticky-facts.txt`
- `branching.txt`
- `task-state-stage1-setup.txt`
- `task-state-stage1-verify.txt`
