# Civitatum Inventory 0.4.0 — восстановление истории

Bundle содержит полную локальную Git-историю, исходники и оригинальные аннотированные метки v0.3.0 и v0.4.0. Восстановление обеих меток и дерева выпуска проверено клонированием. Учётные данные и локальная конфигурация Git в bundle не входят.

```sh
git clone -b codex/civitatum-inventory Civitatum_Inventory_v0.4.0_Git_History.bundle CivitatumInventory
cd CivitatumInventory
git remote rename origin recovery-bundle
git remote add origin https://github.com/ManironRafnrun/civitatum_inventory.git
git show v0.4.0
```

Проверенный локальный коммит: `318710bcd19da5379ff396fd747d4c0993b3c339`. Исходники на сервере: `codex/releases/v0.4.0`. Серверные коммиты connector имеют другой SHA, но точное дерево исходников проверено. Bundle не заменяет новые снимки в `codex/checkpoints/civitatum-inventory`.

SHA-256 bundle: `e3d88cbf4600f79b9a0d15f84c72c7b5618bc6cb57da73d67cdce601a2926cf5`.
