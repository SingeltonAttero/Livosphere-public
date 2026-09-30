---
name: Livosphere — опыт виджетов часов
scope: widgets
status: final
owner_approval: approved-planning-direction
artwork_approval: per-set-pending
updated: 2026-09-09
---


# Виджеты часов — контракт поведения

## Foundation

Native Android AppWidget/RemoteViews, основная поверхность HOME. Визуальная ответственность — [DESIGN](DESIGN.md) и `widgets/<id>/DESIGN.md`; настройка относится к хабу. TextClock является базовым системным временем/датой, AnalogClock проходит отдельный feasibility spike для выбранного art. Произвольная Compose/Canvas/HTML-сцена не обещается внутри RemoteViews. Доказательство WFF или браузерной анимации не является AppWidget evidence.

## Information Architecture

| Поверхность | Вход | Выход |
|---|---|---|
| W1 — Clock HOME | Pin либо picker/configuration host | Тап → выбранное clock app; изменение через H10/host |
| W2 — Configuration | Конкретный appWidgetId от host или hub edit; до pin отдельный draft | Save/Continue/Cancel; протокол [H10](../hub/EXPERIENCE.md) |
| W3 — Clock LOCK | Только подтверждённый host/путь | Системная аутентификация и clock app, если route доступен |
| W4 — Recovery | Clock handler/instance недоступен | H10 выбор нового target/возврат |

## Voice and Tone

«Маленькие — время», «Средние — время и дата», «Крупные — время и дата». Название clock app и выбранный размер доступны в конфигурации; у одинаковых экземпляров есть понятный номер. На самих часах нет технического appWidgetId, debug-сообщений или обязательной инструкции.

## Component Patterns

| Component | Поведение |
|---|---|
| ClockFace | Показывает актуальное время локали/часового пояса телефона, 12/24 формат; системное обновление без polling ради каждой минуты |
| DateLine | M/L получают текущую локализованную дату и корректное обновление при смене суток/часового пояса; S её не показывает |
| WidgetBackdrop | Следует принятой статичной композиции конкретного размера, без обещания wallpaper-like motion |
| ClockTapArea | Открывает выбранный target данного ID, проверяет доступность; LOCK использует системные auth rules, failure идёт W4 |

## State Patterns

Каждый экземпляр хранит `appWidgetId → themeId, semantic size, clock target, schema/revision`. Два одинаковых набора также имеют разные IDs; изменение или удаление одного не сбрасывает другой. Widget preference и wallpaper preference независимы.

| Событие | Контракт |
|---|---|
| Первый pin | Новый draft, запрос host, затем только доказанный ID; request accepted не добавление |
| Success callback | Идемпотентное связывание persistent request token/draft/own ID, проверка provider facts, atomic commit once и initial update; поздний callback не перетирает уже перенастроенный ID; callback не доказывает постоянную видимость widget |
| No callback/cancel | UNKNOWN до проверки; не синтетический success/failed state и не бесконечный spinner |
| Picker/configuration | Работает без предыдущего открытия хаба; неизвестный theme выбирается явно, не берётся из глобального browsing; result OK только после save и initial update. Для pin не полагаться на автоматический запуск configuration Activity |
| Cancel config/edit | Новый несохранённый draft discarded; существующие значения ID неизменны |
| Host resize | Используются реальные bounds и доступный layout; настройки другого ID не меняются |
| Reboot/restore/update | Конфигурации переживают перезапуск/update. При реально полученном host restore — remap old/new IDs и доступные данные; при отсутствии данных явная reconfiguration, не silent theme substitution. Cloud backup/перенос настроек на другое устройство не обещаются; allowBackup=false сохраняется |
| Delete | Очистить только lifecycle-подтверждённый ID; отсутствие в одном UI snapshot не доказательство удаления |
| Clock app удалено | Время остаётся; тап ведёт к локальному выбору доступного target, не ломает обои/другие часы |
| 12/24, локаль, timezone, дата | Системные изменения отражаются без перезапуска хаба; проверяются переходы суток/часового пояса |
| Обои A + часы B1/B2 | Все экземпляры и обои независимы; нет обязательного общего set selection |

Clock app picker использует адресные clock intents и доступные handlers, с узкими `<queries>`, без `QUERY_ALL_PACKAGES`. По умолчанию — системный clock route при наличии. Явно выбранный target не заменяется другим приложением молча; при исчезновении показывается выбор/fallback. Если подходящих handlers нет, сохраняется понятный recovery и часы продолжают показывать время. Нельзя открыть произвольное приложение под видом часов.

## Interaction Primitives

Один тап по читаемой области часов; Android PendingIntent принадлежит конкретному ID и target. Базовый запуск не требует отдельного тапа по мелкой шестерёнке. Размер/target меняются через configuration; удаление/позиция — действия host. Интервальный appWidget `updatePeriodMillis` не используется как минутный таймер; не добавляются FGS, wakelock или постоянное приложение ради времени.

## Accessibility Floor

ClockFace/DateLine имеют уместное доступное описание и читаемый контраст на разных обоях; декоративные элементы не размножают focus. Тап ≥48dp в фактических bounds. Проверяются TalkBack, 200% шрифт configuration, S/M/L, landscape и host clipping. Нет обязательной бесконечной анимации; HTML-предложение движения допустимо только с явной проверкой native-выразимости и корректным reduced/host поведением.

## Responsive & Platform

HOME — обязательный основной путь. Lock screen исследуется по матрице `device/OEM/OS/host/version → capability, add route, sizes, visibility, tap/auth, limitations, evidence`. `supported` ставится только с конкретным подтверждением, `unsupported` и `unknown` не превращаются в универсальное обещание. При подтверждённой возможности путь включается; UNKNOWN не запрещает HOME. Штатные системные часы не заменяются, блокировка не обходится.

Плановые API/size/clock defaults синхронизированы с [архитектурой](../../../architecture/architecture-Livosphere-2026-09-01/ARCHITECTURE-SPINE.md). Фактическая поддержка OEM и artwork feasibility остаются инженерными проверками. Основные источники: [App widget discoverability](https://developer.android.com/develop/ui/views/appwidgets/discoverability), [RemoteViews](https://developer.android.com/reference/android/widget/RemoteViews), [layouts](https://developer.android.com/develop/ui/views/appwidgets/layouts), [lock-screen FAQ](https://android-developers.googleblog.com/2025/03/widgets-on-lock-screen-faq.html). Источники проверены архитектурным подшагом 2026-09-09; это не device evidence.

## Key Flows

### UJ-W1. Илья добавляет вторые часы

1. Илья сохраняет работающий A1, выбирает B и размер M в хабе.
2. В configuration выбирает clock app и подтверждает pin; host размещает B1.
3. **Кульминация:** A1 и B1 одновременно показывают время, B1 также дату, обои неизменны.

Cancel/no callback не удаляют A1; pin unsupported ведёт к проверенному picker route и той же конфигурации.

### UJ-W2. Илья открывает часы и восстанавливает действие

1. Илья нажимает B1 и открывает выбранное приложение часов.
2. Если target удалён, W4 предлагает выбрать доступный target для B1.
3. **Кульминация:** следующее нажатие использует новый target, A1 не меняется.

При отсутствии handlers recovery сохраняет отображение времени. На LOCK система может потребовать разблокировку.

### UJ-W3. Илья меняет размещение часов

1. Илья изменяет размер у host либо возвращается к восстановленному host-экземпляру на поддержанном устройстве.
2. Приложение получает актуальные bounds/ID mapping и доступные настройки; отсутствующие после restore данные требуют понятной reconfiguration.
3. **Кульминация:** часы читаются в новой геометрии; сохранённые настройки использованы, недоступные восстановлены явным выбором.

Некорректный mapping/неподдержанный размер фиксируется как конкретное ограничение; другой ID и обои сохраняются.
