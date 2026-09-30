
# Платформенные основания phone baseline

Проверено 9 сентября 2026. Ниже API documentation evidence, не запуск AppWidget Livosphere. Выбор RemoteViews, отдельных wallpaper components, расписания и numerical widget budgets — решения исполнителя по делегированию.

| Проверенный источник | Что он подтверждает / решение |
| --- | --- |
| [RemoteViews](https://developer.android.com/reference/android/widget/RemoteViews) | Поддержаны TextClock и AnalogClock; произвольные subclasses не поддержаны. Поэтому не обещается прямой перенос Compose/HTML в widget. |
| [TextClock](https://developer.android.com/reference/android/widget/TextClock) | Представление времени/даты с 12/24-часовым форматом и timezone. Host-runtime поведение проверяет PW-01. |
| [AnalogClock](https://developer.android.com/reference/android/widget/AnalogClock) | API и его ограничения рассматриваются отдельно; художественная совместимость и API floor проверяются в early spike. |
| [Widget updates](https://developer.android.com/develop/ui/views/appwidgets/advanced) | updatePeriodMillis имеет минимум 30 минут; 0 отключает periodic updates. Выбран host clock без provider timer. |
| [Pinning](https://developer.android.com/develop/ui/views/appwidgets/discoverability) | Capability проверяется до request; success callback получает ID, неуспех не гарантирует callback. |
| [Configuration](https://developer.android.com/develop/ui/views/appwidgets/configuration) | Отдельная системная configuration Activity и initial update. Настройки сохраняются по экземпляру. |
| [Размеры](https://developer.android.com/develop/ui/views/appwidgets/layouts) | API31+ targetCell sizing и minDP fallback более ранних Android. Фактический размер зависит от host. |
| [AppWidgetProvider](https://developer.android.com/reference/android/appwidget/AppWidgetProvider) | Callback lifecycle, включая onRestored old/new IDs; требуется remap настроек. |
| [WallpaperManager](https://developer.android.com/reference/android/app/WallpaperManager) | ACTION_CHANGE_LIVE_WALLPAPER открывает системный путь для конкретного service component. |
| [Lock-screen FAQ](https://android-developers.googleblog.com/2025/03/widgets-on-lock-screen-faq.html) | Доступность зависит от производителя и host; нельзя выводить поддержку всех телефонов из версии Android или проверки планшета. |

Brownfield сверка: `android/settings.gradle.kts`, `android/gradle/libs.versions.toml` и `android/core/contract/src/main/kotlin/app/livosphere/contract/SetDescriptor.kt` прочитаны в текущем checkout `c756471`. Подтверждены прежние девять модулей, schema 1 с обязательным watchFace и сохранённые версии Stack. AppWidget-модуль, schema 2 и phone-v2 release profile ещё не реализованы. Upgrades не выполнялись; подтверждать «самые свежие» версии для сохранения рабочего baseline не требуется.
