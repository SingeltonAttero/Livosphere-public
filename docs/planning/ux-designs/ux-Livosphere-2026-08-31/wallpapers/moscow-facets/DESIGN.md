
# Москва в гранях — DESIGN

Статус: текущая V1 принята владельцем для замены найденных расхождений и статичного debug-импорта 2026-09-24. Область: `wallpapers/moscow-facets`. Android ID: `moscow-facets-wallpaper`. Source revision: 2.

Источник: `pen-design/Дизайн Живых обоев.pen`, фрейм набора `eBg2l`, review-блок `E6c4B`; точные SHA-256 и Android target — в [карте происхождения](../../../../../development/pencil-wallpaper-source-map.md).

Использовать изображения без художественной перерисовки, рамки телефона, часов, текста и UI. Preview-основа: отдельный hero `m0aAXb`, использующий утренний кадр. Текущие четыре V1-кадра приняты как есть; по сравнению с прежним Android-импортом изменены день и ночь. Внутренние review-замечания Pencil о несовпадении геометрии остаются известным визуальным ограничением этой ревизии. Публичная приёмка и будущая анимация отдельно.

| Фаза | Image node | Source |
| --- | --- | --- |
| утро | `TH15s` | `pen-design/assets/generated-61.png` |
| день | `op1i8` | `pen-design/assets/moscow-facets/day-art-v2.png` |
| вечер | `jcVgA` | `pen-design/assets/generated-55.png` |
| ночь | `beFkR` | `pen-design/assets/moscow-facets/night-art-v3-water.png` |

Viewport: исходник 768×1376, масштабировать с сохранением пропорций и aspect-fill, без чёрных полей. Критичные crop/safe-zone случаи проверить на Android viewport, не считать дизайн-лист системной установкой.
