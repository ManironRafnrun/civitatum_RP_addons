# Полная история CCI 0.1.0-alpha.3

Bundle содержит исходный прототип, alpha.2, alpha.3, метки и резервные снимки исходников. Проверено восстановление в отдельную папку.

Восстановление:

```sh
git clone -b codex/cci/development CCI_0.1.0-alpha.3_Git_History.bundle CivitatumCoupleInteractions
cd CivitatumCoupleInteractions
git show cci/v0.1.0-alpha.3
```

Исходный коммит alpha.3: `e980cb7ac05f24d7ca8841520bccc4fca678f71c`.
Дерево исходников: `cf7e2e904e7e7ff6df38cbb540a88e63ee92ba6d`.
Коммит alpha.2 сохранён: `a9bc6c37575608dbc89e390031390ed2240c27c5`.

Исходники выпуска на GitHub: `codex/cci/releases/0.1.0-alpha.3`.
Коммит GitHub: `e381a74ee59fc8db09f758f69866696bcee8e2f6`.
Коммиты API и исходного Git имеют разные SHA; точное дерево исходников проверено на совпадение.

SHA-256 bundle: `3054cc1d527c1296537936ac930b23c61917a837e22fcf8f8ca638ee6a4977aa`.

Сборка и игровые проверки описаны в `VALIDATION_RU.md` исходников выпуска. Перед продолжением разработки нужно восстановить актуальное подключение и запустить резервное сохранение по `AGENTS.md`. Данные авторизации в bundle не включены.
