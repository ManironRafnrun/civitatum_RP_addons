# Фоны Civitatum Books

Все изображения созданы встроенным генератором изображений специально для Civitatum Books.
В них нет надписей, кнопок, разметки строк или рисунков поверх текста.
Подписи, содержимое документа и элементы управления отрисовываются Java-кодом.
Палитра общая: светлый тёплый пергамент, охристые края, коричневые и приглушённые рыжие детали.

| Ресурс в `src/main/resources/assets/civitatum_books/textures/gui/` | Размер | Назначение |
| --- | --- | --- |
| `open_book.png` | 1650×953 RGBA | Исходный разворот книги |
| `open_scroll.png` | 1254×1254 RGBA | Развёрнутый свиток с верхним и нижним валиками |
| `open_note.png` | 1254×1254 RGBA | Один лист записки с загнутым углом |

Вокруг бумаги сохранена прозрачность. Изображения свитка и записки рисуются без искажения
пропорций в квадрате 500×500, начиная с координат (318, 96). Для этих форматов редактор
использует полотно 1000×660: фон помещается ниже панели форматирования и выше нижних кнопок.
Текст записки занимает область 350×372 с началом (393, 151), текст свитка — окно 350×310
с началом (393, 196). Верхний отступ свитка учитывает валик. Отсечение, выделение,
курсор, иллюстрации и прокрутка используют те же границы.
Оригиналы PNG включены непосредственно в JAR. Python и дополнительные генераторы при сборке не нужны.

Исходный запрос для книги: blank open medieval book, front-on orthographic pixel art,
two blank warm ivory parchment pages, narrow gutter, dark brown and muted russet leather cover,
stepped corners, stacked paper edges, tan shading near edges and spine, transparent surroundings,
no text or controls, no perspective.

Запросы новых фонов: front-on medieval parchment in restrained Minecraft pixel art,
warm ivory text-safe center, tan and ochre edges, limited brown/russet accents,
transparent square canvas, no lettering or controls. Scroll has horizontal top/bottom rolls and
small wooden end caps; note is one loose sheet with a small dog-ear at the upper-right corner.

