---
status: final
updated: 2026-09-17
decision_authority: delegated-executor-under-approved-plan
implementation_status: planned
---


# Phone contracts — наборы, обои и AppWidget

Дополнение к [Architecture Spine](ARCHITECTURE-SPINE.md), обязательное для Epics 8–14. Это технические решения исполнителя по принятому плану. Исходный baseline этих решений — `c756471`: schema1, обязательный WFF и один набор. Нижеследующие контракты реализуются новыми stories; актуальное выполнение отражено в [sprint state](../../../development/sprint-status.yaml), не в описании исходного baseline.

## 1. Schema 2 и выпускной состав

| Поле | Владелец / инвариант |
| --- | --- |
| `schemaVersion=2` | Одна typed schema в core/contract, один parser/validator в build-logic. Неизвестная версия — явная ошибка. |
| `setId`, `setRevision` | Устойчивый lower-kebab ID; revision монотонна, не выводится из имени. |
| `distribution` | `debug-only` либо `public`. Это допуск к variant, отдельно от художественного статуса. |
| `contentStatus` | `draft`, `image-approved`, `html-approved`: только художественная приёмка исходников. Native acceptance и release readiness — внешние записи по digest, не packaged status. |
| `wallpaper` | Один `wallpaperId`, устойчивый service component, version, previewRef, sceneRef, effectsRef и resource refs. |
| `clockWidget` | Один `widgetId`, выбранный `digital` или `analog` формат, S/M/L layouts/previews и resource refs. Все три обязательны для присутствующего widget. Schema2 пока требует также wallpaper; целевое расширение ниже снимает обязательную пару. |
| `sourceAssetsRevision` | Provenance, SHA-256 исходников/exports и ссылки на точные image/HTML approval revisions. |
| `compatibility` | Declared API floor и capabilities отдельно от device evidence. Отсутствие evidence не превращается в PASS. |

Release-variant candidate включает только `distribution=public` + `contentStatus=html-approved`. Это разрешение собрать кандидат, не разрешение публикации. Native/physical/store attestation records привязываются к его digest; тот же immutable artifact после PASS продвигается к публикации без пересборки или изменения packaged manifest. Доставку разрешает внешний release-readiness index, а не contentStatus. Проверка распакованного artifact отклоняет любой ресурс, preview, registry entry, service или widget layout debug-only набора. Пустой допустимый состав означает отказ выпуска, а не fallback на «Контур». Debug использует только телефонные schema 2–4.

Registry и packaging используют один отфильтрованный набор manifests. Set IDs, surface IDs и Android component names уникальны; ресурсные ссылки должны разрешаться внутри допустимого closure. ID не переиспользуется под иной набор. Schema1 и WFF исключены из активного контракта решением [27 сентября](../../decision-2026-09-27-remove-wear.md).

Добавление fixture второго набора проверяет масштабируемость и оболочку; не создаёт вторую принятую публичную тему. Publication readiness требует image approval → HTML approval → сборка immutable candidate → native verification → quality/release evidence для фактического digest и ревизии. Image/HTML approvals содержатся в source metadata до сборки; native/quality outcomes хранятся снаружи APK. Изменение согласованного арта/поведения сбрасывает затронутые approvals до повторного согласования; mere packaging fix не подделывает новое художественное решение.

Candidate до exact-digest проверок подписан ключом целевой публикации; test-signed artifact не продвигается в публичный выпуск. Смена подписи создаёт другой candidate и требует evidence для его digest.

## 2. Владение состоянием и миграции

| Состояние | Storage / источник | Кто изменяет |
| --- | --- | --- |
| `browsingSetId`, выбранная preview surface | Saved presentation state; при необходимости last browsing preference | Hub action/reducer; это не команда применения |
| `WallpaperPreferences[wallpaperId]` | Версионированный typed DataStore | Один WallpaperSettingsRepository transaction writer |
| `WallpaperFact(HOME/LOCK)` | Свежий system adapter: component, observedAt, source, availability | Только platform refresh; requested ID не подставляется как active |
| `WidgetPreferences[appWidgetId]` | Версия schema, widgetId, size, clockTarget, configurationRevision | Один WidgetSettingsRepository writer |
| `WidgetObservation[appWidgetId]` | Свежие own-provider IDs/options и callback evidence | Platform adapter; bound не означает видимый на конкретной поверхности |
| `PendingPin[token]` | Typed DataStore: token, выбранная конфигурация, provider, createdAt, pending/consumed state и связанный ID | Serialized widget flow, не глобальный selectedSet |

Для каждого appWidgetId один RenderCoordinator сериализует RemoteViews публикации из pin/config/onUpdate/options/restore. Он рендерит последний committed configurationRevision; перед отправкой проверяет актуальность revision и generation ID, stale result отбрасывает. Reconfigure r2 не может быть визуально отменён поздним render r1. Delete ставит tombstone/cancel jobs; restore инвалидирует old-ID jobs и передаёт управление new-ID coordinator. Конкурентные IDs независимы; remap locks берутся в устойчивом порядке ID.

Факт конкретного наблюдаемого component соответствует набору только через registry. Если ОС не даёт надёжно различить HOME/LOCK, соответствующее поле остаётся UNKNOWN. Runtime не выводит активность из hub return, installed package или наличия preference.

Миграция старых wallpaper preferences сохраняет возможность распознать прежний ID, но удалённый набор не появляется в каталоге. В release ссылка на отсутствующий debug-only component показывает явное «Оформление недоступно — выберите другое» и доступный системный путь. Не выбирается другая сцена молча. Существующая системная поверхность не переписывается из browsing selection.

Сохраняется текущий `allowBackup=false`: облачный перенос preferences не вводится. Same-install app updates и process recreation используют собственное локальное хранилище. `onRestored` переносит имеющиеся source preferences; без них новый экземпляр требует явной настройки и не получает случайную тему. PendingPin tokens между установками не переносятся. На API30+ `OPTION_APPWIDGET_RESTORE_COMPLETED` выставляется только после успешного remap и initial update, затем выполняется завершающий сериализованный `updateAppWidget`; API29 выполняет восстановление без недоступной константы. Remap, следующий onUpdate и повторный callback сериализованы одним repository writer.

Все migrations versioned, атомарны и idempotent. Corruption/missing reference — typed failure + безопасное восстановление с объяснением; defaults не скрывают потерю выбора. Удаление одного widget ID не затрагивает остальные. При `onRestored(oldIds,newIds)` конфигурации переносятся по парным ID атомарно, обновляются PendingIntent identities и RemoteViews; конфликт/невалидный mapping — восстанавливаемое состояние без случайного присвоения чужих настроек.

## 3. Wallpaper component и preview isolation

Каждый присутствующий wallpaper contribution объявляет отдельный thin `WallpaperService`, связывающий конкретный wallpaperId с общим engine. Component ID устойчив между версиями. Один процесс приложения; каждый active/preview Engine имеет собственные lifecycle, renderer state и отменяемые эффекты.

Примерка B использует `ACTION_CHANGE_LIVE_WALLPAPER` с component B. Уже применённые A продолжают читать A preferences. При отмене B не меняются ни A, ни сохранённые настройки B. Временная настройка системной примерки не пишется в owned preferences до явной команды сохранения соответствующей настройки; callback возврата не является такой командой. При отсутствии прямого system handler — доступный штатный picker/help, при доказанном policy block — объяснение ограничения.

После первой разблокировки service может cold-start без Activity; данные восстанавливаются из repositories и свежих времени/питания. До первой разблокировки поведение измеряется отдельно; Direct Boot не обещан. `isPreview`, visibility и surface validity не смешиваются между Engine. Hidden surface не рисует, destroy освобождает callbacks/resources.

## 4. Четыре фазы и насыщенность

Решение исполнителя: `dawn 05:00–08:00`, `day 08:00–18:00`, `dusk 18:00–21:00`, `night 21:00–05:00`, начало включено, конец исключён. Функция получает Instant и текущий ZoneId; геолокация не нужна. При смене времени/даты/пояса, resume и reboot вычисляется актуальная фаза. Пока surface visible, Engine-owned PhaseScheduler планирует единственный отменяемый in-process callback к ближайшей границе фазы независимо от frame loop и motion=off. На границе static/off кадр перерисовывается один раз и callback переносится к следующей. Hidden/destroy отменяют scheduler; resume пересчитывает текущую фазу и следующий срок. Никаких wake locks, exact alarms или скрытой отрисовки; изменение системного времени пересчитывает срок. Обязательный тест: естественный ход17:59→18:00 при visible/off без TIME_CHANGED. Смена Android dark theme не меняет фазу. DST не требует воспроизведения пропущенных фаз. Исходники обязаны содержать четыре художественно различимых состояния окружения; затемнение одной картинки не выполняет требование.

Для каждой темы составляется таблица `objectId → effectId → trigger → priority → duration/repeat → levels → stop/interrupt → reduced`. Фазы и эффектные слои имеют проверяемые resource refs. Эффекты конкретной сцены могут различаться; универсальный редактор эффектов пользователю не предлагается.

Уровни: `Subtle ⊆ Balanced ⊆ Full`; Full — default художественного состава. Каждый уровень перечисляет конкретные effects, а не только скорость общего transform. `motion=normal/reduced/off` — ограничитель исполнения; saver/low battery/system reduce могут только ужесточить его. Reduced включает разрешённый автором reduced-набор не выше Subtle; off даёт статичный актуальный фазовый кадр. UNKNOWN power — reduced-safe fallback. Порог входа ≤20%, выхода ≥25% при saver off сохраняется.

`interactionsEnabled=false` отдельно отключает tap/swipe без отключения разрешённых фоновых effects. Реакция charging доступна только при объявленной capability и подтверждённом сигнале, не обязательна для каждого набора. Новый trigger отменяет/замещает прежний по таблице приоритетов; очередь декоративных effects не копится. Авторские object motions отличаются от общего сдвига картинки.

## 5. Widget rendering, размеры и время

AppWidgetProvider + RemoteViews XML, без Glance dependency. Native `TextClock` отображает время и дату; host обновляет его независимо от hub. `AnalogClock` разрешён после раннего spike на API29 и целевых hosts: static dial/hour/minute assets и доступные attributes проверяются отдельно. Deprecated API не означает ready art. Custom subclass, Canvas/Compose renderer внутри RemoteViews, минутная перерисовка bitmap, WorkManager timer, foreground service, exact alarms и wakelock не входят в решение.

| Variant | Default cells API31+ | minWidth × minHeight fallback API29/30 | Содержание |
| --- | --- | --- | --- |
| S | 2 × 2 | 110 × 110 dp | Время |
| M | 4 × 2 | 250 × 110 dp | Время и дата |
| L | 4 × 3 | 250 × 180 dp | Время и дата |

Три provider descriptors дают предсказуемый выбор S/M/L, используют общий runtime и theme layouts. Габариты — технический seed, не обещание одинаковой геометрии launcher. Layout учитывает actual host options, padding, clipping, min/max resize; resize не превращает S в другой семантический вариант и не удаляет дату M/L. Неподдерживаемый geometry/art возвращается в локальный дизайн до приёмки.

`updatePeriodMillis=0`: provider реагирует на configuration/update/options/restore callbacks, а не будит приложение для каждой минуты. Clock format уважает 12/24h и locale; timezone — телефонная, дата M/L берётся из локализованного TextClock format. Покрыть минуту, полночь, смену зоны/формата/locale, reboot, process recreation и app update на реальном host. Force-stop проверяется отдельно: ограничения Android не маскируются заявлением непрерывной работы; время после нормального закрытия/kill процесса обязано восстанавливаться без открытия hub.

## 6. Добавление, настройка и нажатие

**Из hub:** пользователь выбирает theme/size/clock app, создаётся временный request token с конфигурацией. При поддержке launcher вызывается requestPinAppWidget для соответствующего provider. Callback связывается с этим request, собственный appWidgetId проверяется через system adapter; после успешного binding сохраняется instance config и выполняется initial update. Проект не полагается на автоматический запуск configuration Activity при pinning. Callback не доказывает HOME/LOCK visibility. Несколько запросов имеют разные tokens и PendingIntent identities; callback старого A не получает browsing B. Callback атомарно переводит persistent token pending→consumed и создаёт только отсутствующую конфигурацию своего ID. Повторный callback идемпотентен и не перезаписывает более новую configurationRevision. При process death между commit и initial update renderer повторно строит RemoteViews из сохранённой конфигурации.

**Из системного picker:** configuration Activity получает appWidgetId, проверяет принадлежность своему provider и допустимые extras, начинает с cancelled result. Save атомарно записывает конфигурацию, вызывает initial update и возвращает OK с ID. Back/cancel не создаёт успешную конфигурацию. Reconfigure сохраняет прежние значения до Save; отмена оставляет их. Ошибка записи/update показывается с Retry, а не successful placement.

**Нет callback:** состояние pending/UNKNOWN, затем ненавязчивая проверка свежих own-widget IDs; время ожидания само не доказывает cancel/failure/success. Повтор разрешён как отдельный request. Неопознанный host ID не получает настройки произвольно: виджет показывает безопасное «Настроить часы» и ведёт в конфигурацию. Pending token действителен 24 часа с createdAt; после expiry остаётся tombstone consumed/expired на 7 дней для защиты от поздних callbacks, затем удаляется. Expiry/cleanup выполняется при callbacks/открытии hub, без фонового scheduler и не доказывает cancel/failure/удаление host widget. Поздний unmatched ID получает needs-configuration, не чужую тему.

**Нет pin capability:** инструкции открыть системный widget picker и найти Livosphere; такой путь также обязан проходить configuration. Не рисуется кнопка, обещающая добавление напрямую на lock screen.

**Clock app:** configuration показывает приложения, разрешающие стандартные clock intents, например `AlarmClock.ACTION_SHOW_ALARMS`, через адресный queries filter. Это выбор clock destination, не инвентаризация установленных приложений. Сохраняется только выбранный ComponentName/action. Нажатие через уникальный instance `PendingIntent.getActivity` попадает в внутреннюю non-exported Activity-router (не service/broadcast trampoline), который заново проверяет доступность разрешённого назначения; удалённый/недоступный target открывает выбор с объяснением. При отсутствии совместимых apps остаются сами часы и действие выбора позже. Системная аутентификация lock screen не обходится. Для targetSdk36 проверяются действующие background-activity-launch правила: widget PendingIntent является пользовательским действием, opt-in/flags применяются только если нужны подтверждённому пути. Пока Activity-router не видим/не может стартовать target, он показывает доступное действие в своём UI; фоновый launch не обходится.

AppWidget providers/callback receivers non-exported; configuration Activity exported для системного host с валидацией ID/provider и пользовательским Save. Explicit PendingIntents уникальны по instance/request; минимальные flags выбираются по нужному platform callback и подтверждаются spike. Непроверенные внешние extras не запускают произвольный component и не переписывают конфигурацию.

## 7. Lock screen и quality gates

Сохраняется штатная eligibility для доступных keyguard hosts, без универсального обещания по Android version. Host support, способ добавления и auth проверяются в отдельной матрице `(model, OS/build, host/launcher, HOME|LOCK, scenario, result, evidence)`. Отсутствующий lock host — ограничение устройства; UNKNOWN — отсутствие данных. Подтверждённый путь реализуется и тестируется; при его отсутствии HOME остаётся доступен. System clock replacement, screensaver и AOD Wear не являются этим scope.

Новые protocol IDs не переиспользуют Wear SP: `PW-01` widget accuracy/lifecycle/restore; `PW-02` HOME/LOCK host routes и authentication; `PW-03` widgets-only и combined energy. Старые phone SP-01/SP-06/SP-07 применяются только с новой версией протокола и evidence нового artifact. Pixel/Samsung/HONOR остаются базовыми phone-семействами до отдельного решения о сужении.

Делегированный дополнительный budget PW-03 до замеров: widgets-only energy delta ≤5% к static wallpaper без widgets; combined normal ≤15%, reduced ≤10% к тому же baseline. Эти лимиты не отменяют отдельные ≤10%/≤5% wallpaper budgets. Измерять одинаковый hardware/build/brightness/network/сценарий/длительность, минимум три сопоставимых пары; шум/непригодность baseline даёт UNKNOWN, а не PASS. Числа — инженерные acceptance criteria, не полученные результаты; протокол уточняет метод/доверие до сбора.

Эмулятор подтверждает API/функциональный путь, но physical battery/performance/OEM/TalkBack и RuStore остаются отдельными gates. Старый `0.1.0-rc.1` не является новым candidate. Подробные источники платформенных решений — [API evidence](platform-evidence-2026-09-09.md).

## 8. Самостоятельный контент — целевой контракт

Основание — [решение 17 сентября](../../sprint-change-proposal-2026-09-17.md). Текущие schema1/2 и их PASS сохраняются как реализованная совместимость; Story 13.3 вводит версионированное расширение, точный номер и поля фиксируются в её implementation SPEC.

- Content package имеет устойчивый ID и вид `widget`, `wallpaper` или `set`; минимум одна настоящая поверхность. Legacy `setId` не переименовывается с потерей ссылок. Комплект объединяет части без общего mutable state.
- Widget-only содержит clock contribution S/M/L, свои preview/assets и AP-HTML; wallpaper/service/phase refs отсутствуют и не подменяются фиктивными ресурсами. Wallpaper-only содержит service/scene/four phases/эффекты/preview с AP-IMAGE/AP-HTML и не требует часов.
- Missing обязательного поля присутствующей поверхности — ошибка; явное отсутствие другой поверхности — допустимый состав. Подделка AP-IMAGE для widget запрещена; N/A описывает неприменимость, не прохождение approval.
- Один manifest остаётся источником generation/packaging/catalog. Текущий ручной `RegistryWidgetCatalog` заменяется соответствующим mapping при первой интеграции; добавление продукта не требует очередной hardcoded ветки в hub/runtime.
- Карточка/CTA выводятся только для присутствующих частей. Нет пустой кнопки обоев у часов; просмотр не меняет установленные поверхности. Минимальный путь первого продукта включён в 13.3, общий каталог завершается 9.1/9.2.
- Сохраняются изоляция IDs/settings, schema1/2 чтение, variant/public guards, original provenance и применимые approvals. Новый формат не делает debug fixture публичным. Native/physical/store evidence остаётся внешним по фактическому artifact.

Полный контракт Story 13.3 остаётся отдельной работой. Для принятых статичных обоев Epic16 реализован узкий debug-вариант ниже; его `schema3` не означает готовность widget-only, HTML или публичного выпуска. Скилл передаёт остальные gaps в 13.3, не обходит validator и не запускает полный audit.

## 9. Epic16: статичные самостоятельные обои для debug

Основание — [принятое изменение 24 сентября](../../sprint-change-proposal-2026-09-24-pencil-wallpapers.md) и Story 16.2 (исторический материал; не является зависимостью текущего документа). `schemaVersion=3` здесь разрешает только `preview` + `wallpaper` с четырьмя разными фазовыми ресурсами и устойчивым `WallpaperService`. Часы, widget refs, scene и effects отсутствуют; `distribution=debug-only` и `contentStatus=image-approved` обязательны. Parser, typed contract и generator одинаково отклоняют неполный состав; schema1/2 и их release policy сохранены.

Хаб строит отдельные проекции обоев и часов из общего registry. Ручной выбор четырёх фаз изменяет только изображение предпросмотра. Установленные обои выбирают фазу по местному времени независимо от хаба; каждый Engine владеет своим worker, bitmap и одним отменяемым callback следующей границы, без постоянного кадра или fade. При скрытии Engine освобождает renderer. Build/тесты подтверждают wiring и общую логику, а фактический HOME, crop, reboot и батарея остаются проверкой владельца на устройстве.
