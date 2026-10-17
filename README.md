# Civitatum RP Addons

Моды Civitatum для Minecraft 1.21.1 / NeoForge 21.1.256. Один проект Gradle, по модулю на мод:

| Модуль | Мод | modId | Версия | jar |
|---|---|---|---|---|
| [inventory](inventory/) | Civitatum Inventory | `civitatum_inventory` | 0.4.1 | `civitatum-inventory-0.4.1.jar` |
| [character-creation](character-creation/) | Civitatum Character Creation | `civitatum` | 0.4.2 | `civitatum-character-creation-0.4.2.jar` |
| [couple-interactions](couple-interactions/) | Civitatum Couple Interactions | `civitatum_couple` | 0.2.0-alpha.1 | `civitatum-couple-interactions-0.2.0-alpha.1.jar` |
| [menu](menu/) | Civitatum Menu | `civitatum_menu` | 0.1.0 | `civitatum-menu-0.1.0.jar` |

Описание, управление и сборка каждого мода — в README его модуля. Все моды распространяются под лицензией MIT ([LICENSE.txt](LICENSE.txt)).

## Сборка

Нужен JDK 21 (Gradle найдёт установленный или скачает сам).

```
./gradlew build                          # все моды, с проверками; jar в <модуль>/build/libs
./gradlew :inventory:build               # один мод
./gradlew :inventory:installToInstance   # собрать и положить в папку mods своей игры
```

Для `installToInstance` один раз укажите путь к своей игре в `instanceDir` в личном `~/.gradle/gradle.properties` (на Windows — `C:\Users\<имя>\.gradle\gradle.properties`), примеры — в `gradle.properties` проекта. Задача удаляет из `mods` прежние версии этого мода и кладёт новую; если игра запущена, Windows не даст удалить старый jar — закройте игру.

В IntelliJ IDEA открывается этот корневой каталог: все моды оказываются в одном окне.

## Устройство

- `settings.gradle` — список модулей и версия плагина ModDevGradle;
- `gradle.properties` — настройки Gradle и версия NeoForge (`neo_version`) для всех модов;
- `build.gradle` — общее для всех модов: Java 21, кодировка UTF-8, Maven Central, версия NeoForge, JUnit 5, группа `team.civitatum`, имя jar `civitatum-<модуль>-<версия>.jar`, лицензия в jar;
- `<модуль>/build.gradle` — только своё у мода: плагины и собственные зависимости;
- `<модуль>/src/main/resources/META-INF/neoforge.mods.toml` — modId и версия мода; Gradle берёт их отсюда.

Исходники собраны из веток `codex/civitatum-inventory`, `codex/ccc/development` и `codex/cci/development` (версии 0.4.1, 0.4.2 и 0.2.0-alpha.1); эти ветки — родители коммита объединения, их история доступна из `main`.
