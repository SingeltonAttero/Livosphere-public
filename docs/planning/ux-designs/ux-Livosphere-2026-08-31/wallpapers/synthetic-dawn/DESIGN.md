
# Синтетический рассвет — DESIGN

Статус: новая серия 04 зафиксирована в текущей V1 и принята владельцем для замены устаревшего Android-импорта 2026-09-24. Область: `wallpapers/synthetic-dawn`. Android ID: `synthetic-dawn-wallpaper`. Source revision: 2. [Запись решения](approval.md).

Источник: `pen-design/Дизайн Живых обоев.pen`, фрейм набора `zIPtz`, review-блок `eVvU4`; точные SHA-256 и Android target — в [карте происхождения](../../../../../development/pencil-wallpaper-source-map.md).

Использовать перечисленные ниже изображения новой серии 04 без художественной перерисовки, рамки телефона, часов, текста и UI. Preview-основа: hero `kZVr5`, использующий дневной кадр. Все четыре прежних Android phase assets заменены. Внутренние review-замечания Pencil о несовпадении геометрии остаются известным ограничением текущей ревизии. Публичная приёмка и будущая анимация отдельно.

| Фаза | Image node | Source |
| --- | --- | --- |
| утро | `DxjfK` | `pen-design/generated-6.png` |
| день | `n00QA` | `pen-design/generated-7.png` |
| вечер | `m1lBYB` | `pen-design/generated-9.png` |
| ночь | `ji5yP` | `pen-design/generated-8.png` |

Viewport: исходник 768×1376, масштабировать с сохранением пропорций и aspect-fill, без чёрных полей. Критичные crop/safe-zone случаи проверить на Android viewport, не считать дизайн-лист системной установкой.
