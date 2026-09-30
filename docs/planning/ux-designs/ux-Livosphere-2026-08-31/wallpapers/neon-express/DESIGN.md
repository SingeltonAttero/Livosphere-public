
# Неоновый экспресс — DESIGN

Статус: текущая V1 серии F05 принята владельцем для замены найденных расхождений и статичного debug-импорта 2026-09-24. Область: `wallpapers/neon-express`. Android ID: `neon-express-wallpaper`. Source revision: 2.

Источник: `pen-design/Дизайн Живых обоев.pen`, фрейм набора `sypox`, review-блок `FfpXE`; точные SHA-256 и Android target — в [карте происхождения](../../../../../development/pencil-wallpaper-source-map.md).

Использовать изображения серии F05 без художественной перерисовки, рамки телефона, часов, текста и UI. Preview-основа: отдельный hero `Z2xvGi` / `generated-10.png`; этот пятый кадр не является runtime-фазой. Все четыре прежних Android phase assets заменены. Внутренние review-замечания Pencil о несовпадении геометрии остаются известным визуальным ограничением этой ревизии. Публичная приёмка и будущая анимация отдельно.

| Фаза | Image node | Source |
| --- | --- | --- |
| утро | `s2TWS` | `pen-design/generated-9.webp` |
| день | `AY06L` | `pen-design/generated-10.webp` |
| вечер | `FMDok` | `pen-design/generated-7.webp` |
| ночь | `CVikA` | `pen-design/generated-8.webp` |

Viewport: исходник 768×1376, масштабировать с сохранением пропорций и aspect-fill, без чёрных полей. Критичные crop/safe-zone случаи проверить на Android viewport, не считать дизайн-лист системной установкой.
