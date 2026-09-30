# Подключение в текущий проект

Все пути ниже относительно корня текущей рабочей копии. Перед правкой сверь HEAD, docs/HANDOFF.md и актуальный код. Публичный контракт — docs/CONTENT.md и docs/architecture/README.md; parser в android/build-logic определяет фактическую схему.

| Граница | Реальная точка входа |
| --- | --- |
| Модель набора/поверхностей | `android/core/contract/src/main/kotlin/app/livosphere/contract/SetDescriptor.kt` |
| Manifest и source assets | `android/sets/<package>/manifest/set.properties`, `source-assets/` |
| Parser/generation/variant selection | `android/build-logic/` — найти владельца через `rg`, не редактировать generated registry |
| Clock catalog adapter | `android/hub/app/src/main/java/app/livosphere/widgets/RegistryWidgetCatalog.kt` |
| Clock runtime/config lifecycle | `android/widgets/runtime/src/main/java/app/livosphere/widgets/runtime/ClockWidgetRuntime.kt` и app widgets adapters |
| Общая отрисовка/фазы обоев | `android/wallpapers/engine/src/main/java/app/livosphere/wallpapers/engine/` |
| Существующие wallpaper contributions | `android/wallpapers/fixture/`, `android/wallpapers/neon/`, `android/wallpapers/static/` — test и production implementations |
| App composition и hub | `android/hub/app/`; Gradle root — `android/` |

## Проверка поддержанного состава

Каталог часов строится из contributions. Самостоятельность применения обоев и виджетов не доказывает поддержку любого нового standalone package: перед добавлением проверь parser, модель descriptors и variant selectors в текущем коде. Не обходи schema/asset validator и не добавляй фиктивный companion.

## Проход интеграции

1. Найди существующий продукт и approvals. Зафиксируй ID/revision и scope; не меняй пользовательские незавершённые файлы.
2. Сопоставь принятые art/HTML элементы с native механизмом и ресурсами. Неподдержанное опиши до изменения согласованного оформления.
3. Подключи exports, manifest, contribution и каталог штатным путём. Новый product-private renderer/layout допустим; общие policies не дублируются.
4. Проверь фильтрацию variant и отсутствие debug-содержимого в выбранном публичном составе в пределах заранее принятого плана. Art-approved candidate ещё не release-ready.
5. Собери затронутый phone debug module и выполни выбранный native путь, сохрани точный APK для владельца.

## Выбор проверок

Команды Gradle выполняются из `android/` с проектным JDK/SDK. Обычная выбранная сборка — `./gradlew :hub:app:assembleDebug -Plivosphere.buildProfile=phone`. Unit selectors, test APK и device cases выбираются по изменённым классам и записываются до запуска. Это пример build-команды, не универсальное разрешение запускать весь suite.

Schema/catalog change требует unit на допустимый самостоятельный пакет, недопустимое отсутствие обязательной части, сохранённые legacy inputs/IDs и variant policy. Widget native путь — add/edit/cancel и соседний instance; wallpaper — apply/preview isolation, фаза/движение/reduced/hidden/resume. Уточняй минимальные selectors по затронутому контракту и тестам. Проверки других неизменённых продуктов переиспользуй как прежнее evidence, с исходной revision.

Не запускай автоматически `make verify`, полный TestKit или APK/source integrity audit. Публичное размещение, постоянный signing и физические показатели относятся к отдельному release scope. Упаковка принятого арта не означает разрешение публикации.
