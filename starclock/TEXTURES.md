# Текстуры M1

Все изображения оригинальные, MIT. Генератор: `tools/generate_textures.py`.
Палитра: ночь `#0B1020`, золото `#E3C07B`, пергамент `#E8DCC0`,
аметист `#8E6FB3`, чернила `#2A2118`. Дополнительные оттенки передают медь, серебро и тени.

Запуск: `./gradlew :starclock:generateTextures`.
Корень вывода: `starclock/build/generated/starclockResources`.
Алгоритм не использует случайность, время или внешние изображения.

| Файлы относительно assets/starclock/textures/item | Размер каждого | Использование |
|---|---|---|
| deck_1.png, deck_2.png, deck_3.png | 16×16 RGBA | Колоды трёх ступеней |
| sextant_1.png, sextant_2.png, sextant_3.png | 16×16 RGBA | Секстанты трёх ступеней |
| bowl_1.png, bowl_2.png, bowl_3.png | 16×16 RGBA | Чаши трёх ступеней |
| ring_1.png, ring_2.png, ring_3.png | 16×16 RGBA | Кольца трёх ступеней |
| tower_key.png | 16×16 RGBA | Ключ Башни |

Модели в `assets/civitatum_starclock/models/item` ссылаются на полные модели
в `assets/starclock/models/item`. Их `layer0` указывает на перечисленные PNG.
Статическая карта M1 рисуется средствами GUI и не обращается к отсутствующим текстурам.
