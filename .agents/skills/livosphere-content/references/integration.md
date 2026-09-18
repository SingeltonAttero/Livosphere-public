# Подключение в текущий проект

Все пути ниже относительно корня текущей рабочей копии. Это навигация по snapshot 2026-09-17; перед правкой сверь HEAD, README и sprint state. Детальный целевой контракт — `_bmad-output/planning-artifacts/architecture/architecture-Livosphere-2026-09-01/phone-contracts.md`, §8. Не поддерживай вторую копию схемы в скилле.

| Граница | Реальная точка входа |
| --- | --- |
| Модель набора/поверхностей | `android/core/contract/src/main/kotlin/app/livosphere/contract/SetDescriptor.kt` |
| Manifest и source assets | `android/sets/<package>/manifest/set.properties`, `source-assets/` |
| Parser/generation/variant selection | `android/build-logic/` — найти владельца через `rg`, не редактировать generated registry |
| Clock catalog adapter | `android/hub/app/src/main/java/app/livosphere/widgets/RegistryWidgetCatalog.kt` |
| Clock runtime/config lifecycle | `android/widgets/runtime/src/main/java/app/livosphere/widgets/runtime/ClockWidgetRuntime.kt` и app widgets adapters |
| Общая отрисовка/фазы обоев | `android/wallpapers/engine/src/main/java/app/livosphere/wallpapers/engine/` |
| Существующие wallpaper contributions | `android/wallpapers/fixture/`, `android/wallpapers/contour/` — lifecycle примеры, debug-art |
| App composition и hub | `android/hub/app/`; Gradle root — `android/` |

## Известный gap первого продукта

На этом snapshot schema2 требует wallpaper + clockWidget + previews + четыре phase refs. Catalog вручную перечисляет два debug widget. Поэтому новые часы нельзя честно «подключить» одним копированием layout или полем `distribution=public`.

Story 13.3 вводит минимальное версионированное расширение для присутствующих поверхностей, с compatibility schema1/2, стабильными IDs и сохранением existing settings. Exact schema/version и изменения генератора определяются в implementation SPEC после чтения кода. Контентная декларация остаётся единственным источником каталогизации; никаких fake wallpaper/approval или обхода empty-public guard.

Минимальный native путь первого виджета включает его preview/card и «Добавить часы». Полное завершение Epic9/10 перед этим не требуется. После реализации 13.3 здесь используй актуальный реализованный контракт; отсутствие устаревшего ограничения подтверждай кодом/проверками.

## Проход интеграции

1. Найди существующий продукт и approvals. Зафиксируй ID/revision и scope; не меняй пользовательские незавершённые файлы.
2. Сопоставь принятые art/HTML элементы с native механизмом и ресурсами. Неподдержанное опиши до изменения согласованного оформления.
3. Подключи exports, manifest, contribution и каталог штатным путём. Новый product-private renderer/layout допустим; общие policies не дублируются.
4. Проверь фильтрацию variant и отсутствие debug-содержимого в выбранном публичном составе в пределах заранее принятого плана. Art-approved candidate ещё не release-ready.
5. Собери затронутый phone debug module и выполни выбранный native путь, сохрани точный APK для владельца.

## Выбор проверок

Команды Gradle выполняются из `android/` с проектным JDK/SDK. Обычная выбранная сборка — `./gradlew :hub:app:assembleDebug -Plivosphere.buildProfile=phone`. Unit selectors, test APK и device cases выбираются по изменённым классам и записываются до запуска. Это пример build-команды, не универсальное разрешение запускать весь suite.

Schema/catalog change требует unit на допустимый самостоятельный пакет, недопустимое отсутствие обязательной части, сохранённые legacy inputs/IDs и variant policy. Widget native путь — add/edit/cancel и соседний instance; wallpaper — apply/preview isolation, фаза/движение/reduced/hidden/resume. Уточняй минимальные selectors в SPEC. Проверки других неизменённых продуктов переиспользуй как прежнее evidence, с исходной revision.

Не запускай автоматически `make verify`, полный TestKit или APK/source integrity audit. Публичное размещение, постоянный signing и физические показатели относятся к отдельному release scope. Упаковка принятого арта не означает разрешение публикации.
