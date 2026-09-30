
# Pencil → Android: текущая V1 и история импорта

## Текущая V1: replacement revision 2

После сверки 2026-09-24 владелец поручил заменить найденные расхождения. Новая воспроизводимая основа находится в отдельном snapshot (исторический материал; не является зависимостью текущего документа); прежний immutable snapshot ниже не изменён и сохраняет provenance предыдущего debug APK.

| Пак / wallpaper ID | Фаза / basis | Pencil frame / image node | Current source asset | SHA-256 | Android target |
| --- | --- | --- | --- | --- | --- |
| Москва в гранях / `moscow-facets-wallpaper` | утро | `eBg2l` / `E6c4B` / `TH15s` | `pen-design/assets/generated-61.png` | `b53cb74bcc0201b5fcfb689e986693023360d39cf91a72fd38e5400519b60357` | `android/sets/moscow-facets/source-assets/wallpaper/drawable-nodpi/ls_moscow_facets_wallpaper_morning.jpg` |
| Москва в гранях / `moscow-facets-wallpaper` | день | `eBg2l` / `E6c4B` / `op1i8` | `pen-design/assets/moscow-facets/day-art-v2.png` | `6bbb2ab399810bee69ffd6b1cbec1e88fa5363c7733798cc5d56ca7067de5d3b` | `android/sets/moscow-facets/source-assets/wallpaper/drawable-nodpi/ls_moscow_facets_wallpaper_day.jpg` |
| Москва в гранях / `moscow-facets-wallpaper` | вечер | `eBg2l` / `E6c4B` / `jcVgA` | `pen-design/assets/generated-55.png` | `9e3a1e5fd6ff1bc118281b83585d6792f208bb35078e0ad7281ffd2f34a3e837` | `android/sets/moscow-facets/source-assets/wallpaper/drawable-nodpi/ls_moscow_facets_wallpaper_evening.jpg` |
| Москва в гранях / `moscow-facets-wallpaper` | ночь | `eBg2l` / `E6c4B` / `beFkR` | `pen-design/assets/moscow-facets/night-art-v3-water.png` | `4b9a35f013d7d178c1816d3bc8c2e8bf1b76db8d5130478a52805fc19806d1f8` | `android/sets/moscow-facets/source-assets/wallpaper/drawable-nodpi/ls_moscow_facets_wallpaper_night.png` |
| Синтетический рассвет / `synthetic-dawn-wallpaper` | утро | `zIPtz` / `eVvU4` / `DxjfK` | `pen-design/generated-6.png` | `fbdcf8e2613a7bb531f04be83f9c320e6003ed332737ad97ba15e97d22da6e6e` | `android/sets/synthetic-dawn/source-assets/wallpaper/drawable-nodpi/ls_synthetic_dawn_wallpaper_morning.jpg` |
| Синтетический рассвет / `synthetic-dawn-wallpaper` | день | `zIPtz` / `eVvU4` / `n00QA` | `pen-design/generated-7.png` | `06a3015479efb1ffe644a250eb47b878f52e653557bd6986a2ecf371fac7f45d` | `android/sets/synthetic-dawn/source-assets/wallpaper/drawable-nodpi/ls_synthetic_dawn_wallpaper_day.jpg` |
| Синтетический рассвет / `synthetic-dawn-wallpaper` | вечер | `zIPtz` / `eVvU4` / `m1lBYB` | `pen-design/generated-9.png` | `130bbc8cf30a363e8109bf136126e938fa470ac92071b4122949f8249d51668c` | `android/sets/synthetic-dawn/source-assets/wallpaper/drawable-nodpi/ls_synthetic_dawn_wallpaper_evening.jpg` |
| Синтетический рассвет / `synthetic-dawn-wallpaper` | ночь | `zIPtz` / `eVvU4` / `ji5yP` | `pen-design/generated-8.png` | `67d19044ab9007348706d391dde25e9f8d57c8cf80fcf27ca612f0990926d3ba` | `android/sets/synthetic-dawn/source-assets/wallpaper/drawable-nodpi/ls_synthetic_dawn_wallpaper_night.jpg` |
| Неоновый экспресс / `neon-express-wallpaper` | утро | `sypox` / `FfpXE` / `s2TWS` | `pen-design/generated-9.webp` | `fa469553dbff0f909ae785a601864cfc004b91c477488cc45368332e16332aaa` | `android/sets/neon-express/source-assets/wallpaper/drawable-nodpi/ls_neon_express_wallpaper_morning.webp` |
| Неоновый экспресс / `neon-express-wallpaper` | день | `sypox` / `FfpXE` / `AY06L` | `pen-design/generated-10.webp` | `a898a3c65b309b4ca7fed8218de57b485c8cbbe0df2b6e57f2b6373e662f730e` | `android/sets/neon-express/source-assets/wallpaper/drawable-nodpi/ls_neon_express_wallpaper_day.webp` |
| Неоновый экспресс / `neon-express-wallpaper` | вечер | `sypox` / `FfpXE` / `FMDok` | `pen-design/generated-7.webp` | `7daec3b41d734744d62722e4965bad720cb238e75caab7568a107b4f906f8ec3` | `android/sets/neon-express/source-assets/wallpaper/drawable-nodpi/ls_neon_express_wallpaper_evening.webp` |
| Неоновый экспресс / `neon-express-wallpaper` | ночь | `sypox` / `FfpXE` / `CVikA` | `pen-design/generated-8.webp` | `4ef4827a68bdcca38323e61702ccbcf5b8e3b306c6ab6cc3255255d57c5a5291` | `android/sets/neon-express/source-assets/wallpaper/drawable-nodpi/ls_neon_express_wallpaper_night.webp` |

Текущие preview exports 384×688 находятся в новом snapshot:

| Пак | Hero / source | SHA-256 |
| --- | --- | --- |
| `moscow-facets` | `m0aAXb` / `assets/generated-61.png` | `bc756e00f5caac80dae0023b801ecb0cb70ac78df429fec6a36702669f1df4d4` |
| `synthetic-dawn` | `kZVr5` / `generated-7.png` | `f753773b371f629f75a96118ea16b2e1a4018fa52446b81b38e05731d7149da8` |
| `neon-express` | `Z2xvGi` / `generated-10.png` | `63931ed717265f221f21667f68eb26c3e70938e869d4498679514b3450ab724c` |

`generated-10.png` у Neon — отдельный preview hero, а не пятая фаза.

## Предыдущий immutable snapshot и debug APK

Источник: `pen-design/Дизайн Живых обоев.pen`; SHA-256 `9c4b305d68bd3908a0d69f5df1e6336368149c98025c2eb610bef88d106244e2`. Проверка документа: 2026-09-24.

Владелец 24 сентября явно принял «Все 40 кадров как есть» и разрешил разработку. Это решение заменяет пометки внутри Pencil о необходимости доработать геометрию только для данного статичного debug-импорта. Исходные файлы оставлены без изменений. Снимок исходника и выбранных 40 файлов (исторический материал; не является зависимостью текущего документа) содержит byte-identical копии для воспроизводимой передачи; остальные кадры дизайн-листа в снимок не входят. Android target ниже — запланированное имя; факт копирования и его checksum проверяются отдельно.

Строки ниже описывают прежний debug APK и не являются текущей V1 для `moscow-facets`, `synthetic-dawn` и `neon-express`. Их SHA-256 сохраняются как исторический provenance.

Все исходники 768×1376. Для экрана 412×892 aspect-fill обрезает примерно 8,6% исходной ширины с каждой стороны; реальные safe zones и главный объект требуют просмотра на устройстве.

| Пак / wallpaper ID | Фаза / basis | Pencil frame / image node | Source asset | SHA-256 | Android target |
| --- | --- | --- | --- | --- | --- |
| Москва в гранях / `moscow-facets-wallpaper` | утро | `eBg2l` / `E6c4B` / `TH15s` | `pen-design/assets/generated-61.png` | `b53cb74bcc0201b5fcfb689e986693023360d39cf91a72fd38e5400519b60357` | `android/sets/moscow-facets/source-assets/wallpaper/drawable-nodpi/ls_moscow_facets_wallpaper_morning.jpg` |
| Москва в гранях / `moscow-facets-wallpaper` | день ★ | `eBg2l` / `E6c4B` / `op1i8` | `pen-design/assets/generated-46.png` | `7a4ed38f796c157dbf278b3de59ccbb95ca36de7ab51babbc92f87efcd28ed28` | `android/sets/moscow-facets/source-assets/wallpaper/drawable-nodpi/ls_moscow_facets_wallpaper_day.jpg` |
| Москва в гранях / `moscow-facets-wallpaper` | вечер | `eBg2l` / `E6c4B` / `jcVgA` | `pen-design/assets/generated-55.png` | `9e3a1e5fd6ff1bc118281b83585d6792f208bb35078e0ad7281ffd2f34a3e837` | `android/sets/moscow-facets/source-assets/wallpaper/drawable-nodpi/ls_moscow_facets_wallpaper_evening.jpg` |
| Москва в гранях / `moscow-facets-wallpaper` | ночь | `eBg2l` / `E6c4B` / `beFkR` | `pen-design/assets/generated-3.webp` | `97c5b813250495830564ad810ff6bf92b19a62c3d103f60e5ac5063d53e9f65f` | `android/sets/moscow-facets/source-assets/wallpaper/drawable-nodpi/ls_moscow_facets_wallpaper_night.webp` |
| Синтетический рассвет / `synthetic-dawn-wallpaper` | утро | `zIPtz` / `eVvU4` / `DxjfK` | `pen-design/assets/generated-59.png` | `01d03118b587b845efc080a11703ce6ab168c3d8dcdc10fd75f443db9b1a40a0` | `android/sets/synthetic-dawn/source-assets/wallpaper/drawable-nodpi/ls_synthetic_dawn_wallpaper_morning.jpg` |
| Синтетический рассвет / `synthetic-dawn-wallpaper` | день ★ | `zIPtz` / `eVvU4` / `n00QA` | `pen-design/assets/generated-54.png` | `49f4bf018ee008445d9634e2505553676d5bf978532fe608bcb3c21013a9c97a` | `android/sets/synthetic-dawn/source-assets/wallpaper/drawable-nodpi/ls_synthetic_dawn_wallpaper_day.jpg` |
| Синтетический рассвет / `synthetic-dawn-wallpaper` | вечер | `zIPtz` / `eVvU4` / `m1lBYB` | `pen-design/assets/generated-63.png` | `ab87321e45f6cf705c1b669b921b65ed240f5d6fe538198f17c0fce6d315eff5` | `android/sets/synthetic-dawn/source-assets/wallpaper/drawable-nodpi/ls_synthetic_dawn_wallpaper_evening.jpg` |
| Синтетический рассвет / `synthetic-dawn-wallpaper` | ночь | `zIPtz` / `eVvU4` / `ji5yP` | `pen-design/assets/generated-5.webp` | `42472a737ee011cb39e069c1c1ec51ae272f34fe603790e4a5b5e8d52eed8933` | `android/sets/synthetic-dawn/source-assets/wallpaper/drawable-nodpi/ls_synthetic_dawn_wallpaper_night.webp` |
| Неоновый экспресс / `neon-express-wallpaper` | утро | `sypox` / `FfpXE` / `s2TWS` | `pen-design/assets/generated-1.webp` | `05543f33326584a6da7eb733ecec42f109a59dcc0bf3dea1922845be3ff82b5a` | `android/sets/neon-express/source-assets/wallpaper/drawable-nodpi/ls_neon_express_wallpaper_morning.webp` |
| Неоновый экспресс / `neon-express-wallpaper` | день | `sypox` / `FfpXE` / `AY06L` | `pen-design/assets/generated.webp` | `8cc9bb83698a5390ff3466b4c09ae515aa73df992c006624b9f0d6c6ec4b1f49` | `android/sets/neon-express/source-assets/wallpaper/drawable-nodpi/ls_neon_express_wallpaper_day.webp` |
| Неоновый экспресс / `neon-express-wallpaper` | вечер ★ | `sypox` / `FfpXE` / `FMDok` | `pen-design/assets/generated-27.png` | `b92469fbb7b1129b0046d3d9b3bf2726bae25e327ea52ffafa10dfb72981b3df` | `android/sets/neon-express/source-assets/wallpaper/drawable-nodpi/ls_neon_express_wallpaper_evening.jpg` |
| Неоновый экспресс / `neon-express-wallpaper` | ночь | `sypox` / `FfpXE` / `CVikA` | `pen-design/assets/generated-2.webp` | `b75cb466ff2df6096d187c65a46c072fc48e6a4c10202243870dd43972ca8f4d` | `android/sets/neon-express/source-assets/wallpaper/drawable-nodpi/ls_neon_express_wallpaper_night.webp` |
| Лес после дождя / `rainforest-wallpaper` | утро | `k7svp` / `X7FBzU` / `eOUcx` | `pen-design/assets/generated-85.png` | `f2705903dcfaaa9efa021d1052cb571438d88b94c7cfbe19fdb82a1491b0ca1b` | `android/sets/rainforest/source-assets/wallpaper/drawable-nodpi/ls_rainforest_wallpaper_morning.jpg` |
| Лес после дождя / `rainforest-wallpaper` | день ★ | `k7svp` / `X7FBzU` / `cE7TM` | `pen-design/assets/generated-19.png` | `585e0030bde4f61acd3a8de6c7ddda699270380189a422a98843ef146a530a27` | `android/sets/rainforest/source-assets/wallpaper/drawable-nodpi/ls_rainforest_wallpaper_day.jpg` |
| Лес после дождя / `rainforest-wallpaper` | вечер | `k7svp` / `X7FBzU` / `tR9bI` | `pen-design/assets/generated-4.webp` | `78f2ca22adefd556f0f32f520c9bac08e42ffbe599601a9662051e7fbe5f4b1b` | `android/sets/rainforest/source-assets/wallpaper/drawable-nodpi/ls_rainforest_wallpaper_evening.webp` |
| Лес после дождя / `rainforest-wallpaper` | ночь | `k7svp` / `X7FBzU` / `GU47O` | `pen-design/assets/generated-86.png` | `698d0f3c1871877db13071d0bd891e574973f812a796b817417be06e6947e655` | `android/sets/rainforest/source-assets/wallpaper/drawable-nodpi/ls_rainforest_wallpaper_night.jpg` |
| Изумрудная бухта / `emerald-cove-wallpaper` | утро | `P89LP0` / `ggdif` / `MyaXp` | `pen-design/assets/generated-67.png` | `c9b924dc874ca98cd89ba4d15e247af91a68260544cd5fb277b8d4476c16b074` | `android/sets/emerald-cove/source-assets/wallpaper/drawable-nodpi/ls_emerald_cove_wallpaper_morning.jpg` |
| Изумрудная бухта / `emerald-cove-wallpaper` | день | `P89LP0` / `ggdif` / `Cb8Tv` | `pen-design/assets/generated-68.png` | `058b937c1122ba4796d5e4d7818736fe902a9e05c45beb8b32048ab3d458b406` | `android/sets/emerald-cove/source-assets/wallpaper/drawable-nodpi/ls_emerald_cove_wallpaper_day.jpg` |
| Изумрудная бухта / `emerald-cove-wallpaper` | вечер ★ | `P89LP0` / `ggdif` / `etonT` | `pen-design/assets/generated-28.png` | `c2e8a9147e911b0592010e6bea00aa52ba7a81436e092692587f1ef76e70d9f8` | `android/sets/emerald-cove/source-assets/wallpaper/drawable-nodpi/ls_emerald_cove_wallpaper_evening.jpg` |
| Изумрудная бухта / `emerald-cove-wallpaper` | ночь | `P89LP0` / `ggdif` / `ZzdVw` | `pen-design/assets/generated-69.png` | `b07981d0883e71621b774a530ad977b16c95593826e0c2d5dc26936627136a9a` | `android/sets/emerald-cove/source-assets/wallpaper/drawable-nodpi/ls_emerald_cove_wallpaper_night.jpg` |
| Окно на орбиту / `orbital-window-wallpaper` | утро | `gi7hk` / `GcEbJ` / `wxPlr` | `pen-design/assets/generated-65.png` | `bdc150224ce6b9724deae88461537c0a4eb0817793f6d243c213746a239c60bf` | `android/sets/orbital-window/source-assets/wallpaper/drawable-nodpi/ls_orbital_window_wallpaper_morning.jpg` |
| Окно на орбиту / `orbital-window-wallpaper` | день | `gi7hk` / `GcEbJ` / `lxBLq` | `pen-design/assets/generated-64.png` | `8f5e1e5b75b95ee89bf6d705174779c3e490658d14fae6e0ce70400dfa6e1381` | `android/sets/orbital-window/source-assets/wallpaper/drawable-nodpi/ls_orbital_window_wallpaper_day.jpg` |
| Окно на орбиту / `orbital-window-wallpaper` | вечер | `gi7hk` / `GcEbJ` / `VQdCc` | `pen-design/assets/generated-71.png` | `49d81d259c0723a2da0d3819464fbbe067b30afc718d5a5303c6f1a49e8f71ad` | `android/sets/orbital-window/source-assets/wallpaper/drawable-nodpi/ls_orbital_window_wallpaper_evening.jpg` |
| Окно на орбиту / `orbital-window-wallpaper` | ночь ★ | `gi7hk` / `GcEbJ` / `W9985` | `pen-design/assets/generated-51.png` | `fbb499038d072506099a44b5f2d36fee656cd230c0536eaac88920e60a676673` | `android/sets/orbital-window/source-assets/wallpaper/drawable-nodpi/ls_orbital_window_wallpaper_night.jpg` |
| Звёздная река / `star-river-wallpaper` | утро | `ae5uH` / `u6O4pQ` / `Htlxo` | `pen-design/assets/generated-84.png` | `fd3510e49852154995e32a465499e9b2c135a8eaa2715834888abe984e8d9b25` | `android/sets/star-river/source-assets/wallpaper/drawable-nodpi/ls_star_river_wallpaper_morning.jpg` |
| Звёздная река / `star-river-wallpaper` | день | `ae5uH` / `u6O4pQ` / `BScey` | `pen-design/assets/generated-77.png` | `3c2f68c0ff4a77eea41958bb723e0dfb91d50925d36a6942071709ca37603b5a` | `android/sets/star-river/source-assets/wallpaper/drawable-nodpi/ls_star_river_wallpaper_day.jpg` |
| Звёздная река / `star-river-wallpaper` | вечер | `ae5uH` / `u6O4pQ` / `HmUg8` | `pen-design/assets/generated-80.png` | `4fe99ceb34b16d6ee46a2c691b860bae9526ea8755b5bc55a6bc632ea90a1892` | `android/sets/star-river/source-assets/wallpaper/drawable-nodpi/ls_star_river_wallpaper_evening.jpg` |
| Звёздная река / `star-river-wallpaper` | ночь ★ | `ae5uH` / `u6O4pQ` / `cRRfU` | `pen-design/assets/generated-44.png` | `f0a627fdbb9b9d23885476b3c50bb96d79e73f34a08feb66d39b744e217d1237` | `android/sets/star-river/source-assets/wallpaper/drawable-nodpi/ls_star_river_wallpaper_night.jpg` |
| Золотые барханы / `golden-dunes-wallpaper` | утро | `juFgH` / `AvJo2` / `O7nTkG` | `pen-design/assets/generated-73.png` | `a722fe311959e572f48ace5a9a8b3e5b51d4987bda30ff9761c0bdc9f538c046` | `android/sets/golden-dunes/source-assets/wallpaper/drawable-nodpi/ls_golden_dunes_wallpaper_morning.jpg` |
| Золотые барханы / `golden-dunes-wallpaper` | день ★ | `juFgH` / `AvJo2` / `w4BpaE` | `pen-design/assets/generated-26.png` | `def13128ab38c8e3bf890739990702a67b2cd529c034dc9e6ddcb24b54e8db35` | `android/sets/golden-dunes/source-assets/wallpaper/drawable-nodpi/ls_golden_dunes_wallpaper_day.jpg` |
| Золотые барханы / `golden-dunes-wallpaper` | вечер | `juFgH` / `AvJo2` / `VCjoT` | `pen-design/assets/generated-75.png` | `4ff0bab5b7e2921983f7eaadc284d947caa24d8a7adfcfa79b7aeda068277ff7` | `android/sets/golden-dunes/source-assets/wallpaper/drawable-nodpi/ls_golden_dunes_wallpaper_evening.jpg` |
| Золотые барханы / `golden-dunes-wallpaper` | ночь | `juFgH` / `AvJo2` / `a9tOe` | `pen-design/assets/generated-82.png` | `2ae660b5bc0c2513d8382d5f99696bc78c98b70f7ca0cd45765bc15a4de4cf79` | `android/sets/golden-dunes/source-assets/wallpaper/drawable-nodpi/ls_golden_dunes_wallpaper_night.jpg` |
| Лунный прилив / `moon-tide-wallpaper` | утро | `kco4k` / `OqILQ` / `VV3nl` | `pen-design/assets/generated-76.png` | `d8e9e4c7aece0227a252c9937e15576d329f03124018185f70036e4342f6797c` | `android/sets/moon-tide/source-assets/wallpaper/drawable-nodpi/ls_moon_tide_wallpaper_morning.jpg` |
| Лунный прилив / `moon-tide-wallpaper` | день | `kco4k` / `OqILQ` / `PPd7V` | `pen-design/assets/generated-74.png` | `533d23a3a2f9eeaffcaf1e0ae4b4a0a3569f0c77e22a3fe16e53867b4515400d` | `android/sets/moon-tide/source-assets/wallpaper/drawable-nodpi/ls_moon_tide_wallpaper_day.jpg` |
| Лунный прилив / `moon-tide-wallpaper` | вечер | `kco4k` / `OqILQ` / `EtMRo` | `pen-design/assets/generated-83.png` | `3d94c75b90a02a3fb86c8ab681c6dfd7c2f3de5515efa7f7e44031216f843e59` | `android/sets/moon-tide/source-assets/wallpaper/drawable-nodpi/ls_moon_tide_wallpaper_evening.jpg` |
| Лунный прилив / `moon-tide-wallpaper` | ночь ★ | `kco4k` / `OqILQ` / `aOIT0` | `pen-design/assets/generated-25.png` | `206d1968f419c146fdcc6917c0d2f762c4127a75400a4ef103c226b913c1f2a2` | `android/sets/moon-tide/source-assets/wallpaper/drawable-nodpi/ls_moon_tide_wallpaper_night.jpg` |
| Хроматический поток / `chromatic-flow-wallpaper` | утро | `mrzQo` / `LDG3a` / `AGi9b` | `pen-design/assets/generated-81.png` | `182d3387eabb00b8e14253be857778033dbc25ca89c0019df3c093fe8f4ffcdf` | `android/sets/chromatic-flow/source-assets/wallpaper/drawable-nodpi/ls_chromatic_flow_wallpaper_morning.jpg` |
| Хроматический поток / `chromatic-flow-wallpaper` | день ★ | `mrzQo` / `LDG3a` / `wA9Ib` | `pen-design/assets/generated-33.png` | `e7d960aefa4fc8a8ab79cd80b6a54acefddc0fc28286ab3e96ce65407ec990ca` | `android/sets/chromatic-flow/source-assets/wallpaper/drawable-nodpi/ls_chromatic_flow_wallpaper_day.jpg` |
| Хроматический поток / `chromatic-flow-wallpaper` | вечер | `mrzQo` / `LDG3a` / `qd2XL` | `pen-design/assets/generated-78.png` | `a183ceb3544e57a5b8054ebc5dd7969ccc1ba12531b64bdc9f319eb3b86e1d3f` | `android/sets/chromatic-flow/source-assets/wallpaper/drawable-nodpi/ls_chromatic_flow_wallpaper_evening.jpg` |
| Хроматический поток / `chromatic-flow-wallpaper` | ночь | `mrzQo` / `LDG3a` / `W175aG` | `pen-design/assets/generated-79.png` | `0fa72838787371f6a5534e74efded29f9c3566b36d47389eacc14a322a4a3eaf` | `android/sets/chromatic-flow/source-assets/wallpaper/drawable-nodpi/ls_chromatic_flow_wallpaper_night.jpg` |

Названия Android target зафиксированы для handoff. Источник Pencil может иметь расширение `.png` при JPEG-кодировке; Android target получает расширение по фактическому формату без изменения принятых байтов. Перед сборкой manifest/checksums должны ссылаться на реальные файлы с теми же SHA-256; изменение bytes требует новой revision и проверки art approval.

## Предыдущие preview exports (revision 1)

Отдельные облегчённые PNG получены без изменения композиции через `sips -Z 688 -s format png` из выбранной основы каждого пака. Все 10 имеют размер 384×688; это preview, не полноразмерный wallpaper plate.

| Пак | Основа / image node | Файл | SHA-256 |
| --- | --- | --- | --- |
| `moscow-facets` | День / `op1i8` | `_bmad-output/implementation-artifacts/source/pencil-2026-09-24/previews/ls_moscow_facets_preview_wallpaper.png` | `a3004b38bfd970eaef672e80a53c77f377b375e5b90b89b35db6b9d0abac0ea7` |
| `synthetic-dawn` | День / `n00QA` | `_bmad-output/implementation-artifacts/source/pencil-2026-09-24/previews/ls_synthetic_dawn_preview_wallpaper.png` | `f23d9f937f8ed29c3decc1e1c3d0181ce878c8cbd057a824c594025f6675cb54` |
| `neon-express` | Вечер / `FMDok` | `_bmad-output/implementation-artifacts/source/pencil-2026-09-24/previews/ls_neon_express_preview_wallpaper.png` | `e2b0216df93af613503c7a21e635463ac7f53621ef28e7aea92cfad1ba8564a9` |
| `rainforest` | День / `cE7TM` | `_bmad-output/implementation-artifacts/source/pencil-2026-09-24/previews/ls_rainforest_preview_wallpaper.png` | `c0d874690d7b5420dbd4158ce55c4050905ac49023e419b4c2aadba84908134d` |
| `emerald-cove` | Вечер / `etonT` | `_bmad-output/implementation-artifacts/source/pencil-2026-09-24/previews/ls_emerald_cove_preview_wallpaper.png` | `f7e5913009dea761698db0b45ad8795ffe00ec850779628b42ae7bb07af88a8b` |
| `orbital-window` | Ночь / `W9985` | `_bmad-output/implementation-artifacts/source/pencil-2026-09-24/previews/ls_orbital_window_preview_wallpaper.png` | `ad0ba409308ad4dbd70d1f51e55513bc5d30e618bdcd1206d9fcc7ba4cbfd5be` |
| `star-river` | Ночь / `cRRfU` | `_bmad-output/implementation-artifacts/source/pencil-2026-09-24/previews/ls_star_river_preview_wallpaper.png` | `a1672f2d2e3944934d42fce83964d4ce8432829e7b5b2a63ec3cfe4c1703fb67` |
| `golden-dunes` | День / `w4BpaE` | `_bmad-output/implementation-artifacts/source/pencil-2026-09-24/previews/ls_golden_dunes_preview_wallpaper.png` | `6984f1e1e16fd3a3f258f9522600c71e4a4c98a16217a881a029d7067054c7b9` |
| `moon-tide` | Ночь / `aOIT0` | `_bmad-output/implementation-artifacts/source/pencil-2026-09-24/previews/ls_moon_tide_preview_wallpaper.png` | `1e964f5fe41079acb0bf9179b6592a9b9c8c73fe410836c828d8194c7d0c882b` |
| `chromatic-flow` | День / `wA9Ib` | `_bmad-output/implementation-artifacts/source/pencil-2026-09-24/previews/ls_chromatic_flow_preview_wallpaper.png` | `a8913d5d67ac46ae10965a63cbfc619104b0fbddcc7d2ffaca36bd8e3c77e9e9` |

## Публичный checkout

Пути `_bmad-output/implementation-artifacts/source/` в таблице — происхождение исторических экспортов, не зависимости сборки. Raw evidence остаётся в приватном архиве. Рабочий `.pen` и доступные локальные зависимости перечислены в [manifest](../../pen-design/source-manifest.json); Android-ресурсы находятся в `android/sets/`. Эта карта не подтверждает новую native или физическую приёмку.
