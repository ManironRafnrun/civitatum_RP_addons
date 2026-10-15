# Резервное копирование CCC

Во время активной разработки `tools/git_checkpoint.py watch` сохраняет исходники каждые 10 минут в `codex/checkpoints/ccc`. Продление активности: `python3 tools/git_checkpoint.py heartbeat`. Однократная контрольная точка: `python3 tools/git_checkpoint.py once`. Успех подтверждается `REMOTE_VERIFIED`, с проверкой дерева и ссылки ветки.

Учётные данные не входят в проект. Конфигурация текущего сеанса хранится только внутри `.git/civitatum-backup/`. Релизы размещаются в `codex/ccc/releases/*`, полная нативная история — в отдельной ветке `codex/ccc/history/*`. Ветки CI, CCI и main не изменяются.
