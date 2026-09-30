# Коллекции и источники

Редактируемый арт находится в `pen-design/Дизайн Живых обоев.pen`. Ресурсы текущей Android-сборки находятся в `android/sets/<id>/source-assets/`. Их точные версии и SHA-256 записаны в manifest и `checksums.sha256`.

| Коллекция | Обои | Узлы исходной композиции | Текущий состав |
| --- | --- | --- | --- |
| `chromatic-flow` | Четыре фазовых изображения | `AGi9b`, `wA9Ib`, `qd2XL`, `W175aG` | [Описание и ресурсы](../../android/sets/chromatic-flow/manifest/set.properties) |
| `electric-harbor` | Анимированная сцена | См. запись приёмки | [Описание и ресурсы](../../android/sets/electric-harbor/manifest/set.properties) |
| `emerald-cove` | Четыре фазовых изображения | `MyaXp`, `Cb8Tv`, `etonT`, `ZzdVw` | [Описание и ресурсы](../../android/sets/emerald-cove/manifest/set.properties) |
| `golden-dunes` | Четыре фазовых изображения | `O7nTkG`, `w4BpaE`, `VCjoT`, `a9tOe` | [Описание и ресурсы](../../android/sets/golden-dunes/manifest/set.properties) |
| `last-light` | Анимированная сцена | См. запись приёмки | [Описание и ресурсы](../../android/sets/last-light/manifest/set.properties) |
| `moon-tide` | Четыре фазовых изображения | `VV3nl`, `PPd7V`, `EtMRo`, `aOIT0` | [Описание и ресурсы](../../android/sets/moon-tide/manifest/set.properties) |
| `moscow-facets` | Четыре фазовых изображения | `TH15s`, `op1i8`, `jcVgA`, `beFkR`, `op1i8`, `beFkR`, `m0aAXb` | [Описание и ресурсы](../../android/sets/moscow-facets/manifest/set.properties) |
| `neon-express` | Четыре фазовых изображения | `s2TWS`, `AY06L`, `FMDok`, `CVikA`, `Z2xvGi` | [Описание и ресурсы](../../android/sets/neon-express/manifest/set.properties) |
| `night-sakura` | Анимированная сцена | См. запись приёмки | [Описание и ресурсы](../../android/sets/night-sakura/manifest/set.properties) |
| `orbital-window` | Четыре фазовых изображения | `wxPlr`, `lxBLq`, `VQdCc`, `W9985` | [Описание и ресурсы](../../android/sets/orbital-window/manifest/set.properties) |
| `rainforest` | Четыре фазовых изображения | `eOUcx`, `cE7TM`, `tR9bI`, `GU47O` | [Описание и ресурсы](../../android/sets/rainforest/manifest/set.properties) |
| `star-river` | Четыре фазовых изображения | `Htlxo`, `BScey`, `HmUg8`, `cRRfU` | [Описание и ресурсы](../../android/sets/star-river/manifest/set.properties) |
| `synthetic-dawn` | Четыре фазовых изображения | `DxjfK`, `n00QA`, `m1lBYB`, `ji5yP`, `kZVr5` | [Описание и ресурсы](../../android/sets/synthetic-dawn/manifest/set.properties) |

Для каждой коллекции `approval.md` и `widget-approval.md` сохраняют принятые автором ревизии; `source-assets/PROVENANCE.md` описывает происхождение ресурсов и шрифтов. Три анимированные коллекции используют фазовые пластины и отдельные слои. Для остальных коллекций принимается кадр каждой фазы; runtime не превращает эти изображения в полноценную анимированную сцену.

Часы и обои используют независимые ресурсы и могут сочетаться между коллекциями. Переименование файлов дизайна не меняет Android-ресурсы автоматически.
