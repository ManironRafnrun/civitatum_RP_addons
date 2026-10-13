# CCI: история прототипа и выпуска alpha.2

Bundle содержит полную локальную историю CCI, оригинальный прототип и аннотированные метки cci/prototype-alpha.1 и cci/v0.1.0-alpha.2. Восстановление обеих меток и дерева проверенного выпуска проверено клонированием. Авторизация и локальные конфиги Git в bundle не входят.

```sh
git clone -b codex/cci/development CCI_0.1.0-alpha.2_Git_History.bundle CivitatumCoupleInteractions
cd CivitatumCoupleInteractions
git remote rename origin recovery-bundle
git remote add origin https://github.com/ManironRafnrun/civitatum_inventory.git
git show cci/v0.1.0-alpha.2
```

Проверенный локальный коммит: `a9bc6c37575608dbc89e390031390ed2240c27c5`. Серверные исходники: `codex/cci/releases/0.1.0-alpha.2`. Последние рабочие снимки: `codex/checkpoints/cci`. Серверные SHA connector отличаются от локальных; точное дерево исходников проверено.

SHA-256 bundle: `123eff735f3c46e6504e699a44cedc7587776fcdd476ce175d7496cbeeab3b66`.
