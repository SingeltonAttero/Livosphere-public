
# Лес после дождя — DESIGN

Статус: принятые владельцем Pencil-кадры для статичного debug-импорта 2026-09-24. Область: `wallpapers/rainforest`. Android ID: `rainforest-wallpaper`.

Источник: `pen-design/Дизайн Живых обоев.pen`, фрейм набора `k7svp`, review-блок `X7FBzU`; точные SHA-256 и Android target — в [карте происхождения](../../../../../development/pencil-wallpaper-source-map.md).

Использовать изображения без художественной перерисовки, рамки телефона, часов, текста и UI. Выбранная основа: день. Все четыре кадра приняты как есть. Внутренние review-замечания Pencil о несовпадении геометрии остаются известным визуальным ограничением этой ревизии. Публичная приёмка и будущая анимация отдельно.

| Фаза | Image node | Source |
| --- | --- | --- |
| утро | `eOUcx` | `pen-design/assets/generated-85.png` |
| день | `cE7TM` | `pen-design/assets/generated-19.png` |
| вечер | `tR9bI` | `pen-design/assets/generated-4.webp` |
| ночь | `GU47O` | `pen-design/assets/generated-86.png` |

Viewport: исходник 768×1376, масштабировать с сохранением пропорций и aspect-fill, без чёрных полей. Критичные crop/safe-zone случаи проверить на Android viewport, не считать дизайн-лист системной установкой.
