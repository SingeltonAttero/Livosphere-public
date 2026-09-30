---
id: SPEC-livosphere
status: final
updated: 2026-09-17
baseline: content-first-planning-2026-09-17
companions:
  - ../../planning/prds/prd-Livosphere-2026-08-28/prd.md
  - ../../planning/prds/prd-Livosphere-2026-08-28/addendum.md
  - ../../planning/architecture/architecture-Livosphere-2026-09-01/ARCHITECTURE-SPINE.md
  - ../../planning/architecture/architecture-Livosphere-2026-09-01/phone-contracts.md
  - ../../planning/ux-designs/ux-Livosphere-2026-08-31/DESIGN.md
  - ../../planning/ux-designs/ux-Livosphere-2026-08-31/EXPERIENCE.md
  - ../../planning/ux-designs/ux-Livosphere-2026-08-31/hub/DESIGN.md
  - ../../planning/ux-designs/ux-Livosphere-2026-08-31/hub/EXPERIENCE.md
  - ../../planning/ux-designs/ux-Livosphere-2026-08-31/widgets/DESIGN.md
  - ../../planning/ux-designs/ux-Livosphere-2026-08-31/widgets/EXPERIENCE.md
  - ../../planning/ux-designs/ux-Livosphere-2026-08-31/wallpapers/CONTRACT.md
  - ../../planning/ux-designs/ux-Livosphere-2026-08-31/sets/CONTRACT.md
  - capability-history.md
sources:
  - ../../planning/decision-2026-09-09-phone-themes-pivot.md
---


# Livosphere — phone contract

Этот SPEC и перечисленные companions задают полный контракт нового scope. Исторические Wear-макеты, старые stories и evidence не являются текущими требованиями или новым PASS.

## Why

Владелец меняет сложный путь оформления смарт-часов и маловыразительный тестовый комплект на доступное авторское оформление Android-телефона: живые сцены и собственные часы, выбираемые независимо. Продукт проверяет привлекательность/сохранение оформления и возможность регулярно создавать наборы силами одного владельца; спрос пока не доказан.

## Capabilities

- **CAP-1**
  - **intent:** Пользователь выбирает локальные обои, виджеты или комплекты, рассматривает имеющиеся поверхности, проходит или пропускает знакомство.
  - **success:** Витрина/preview показывают нужную ревизию и S/M/L; onboarding не задерживает CTA, повторяется не чаще недели только при подтверждённом неприменении; ACTIVE/UNKNOWN подавляют повтор. Изменение browsing не меняет установленные поверхности.

- **CAP-2**
  - **intent:** Пользователь применяет выбранные живые обои кратчайшим подтверждённым системным путём и восстанавливается после отмены или ограничения.
  - **success:** Примерка B не меняет уже применённые A при отмене; HOME/LOCK, совместимость и применение различаются. UNKNOWN/отсутствие handler/policy block имеют честный доступный следующий шаг без ложного ACTIVE.

- **CAP-3**
  - **intent:** Установленные обои показывают живые объекты и четыре фазы суток с управляемой насыщенностью и уместными реакциями.
  - **success:** Рассвет/день/закат/ночь зависят от местного времени, а не Android dark theme; authored objects/effects/levels проверяемы. Power/reduced/off и отдельный toggle tap/swipe соблюдаются; charging/offset только при поддержке; hidden/cold-start/multi-Engine корректны и quality budgets подтверждены отдельными проверками.

- **CAP-6**
  - **intent:** Пользователь независимо настраивает движение, получает помощь и сведения о продукте и значимых обновлениях.
  - **success:** Hub motion не наследует арт; неподдержанные controls скрыты либо объяснены. Новые темы приходят с app update, локальная история соответствует версии, повторные визиты не являются продуктовой целью; настройки обоев и каждого widget сохраняются.

- **CAP-7**
  - **intent:** Владелец воспроизводимо выпускает телефонное приложение с проверенными наборами через RuStore.
  - **success:** Phone release profile включает hub и поверхности принятого состава без Wear и debug-only content, signed artifact/revisions/hashes совпадают с tests и physical/store evidence. Постоянный ключ внешний, публикация отдельно разрешена; legacy candidate не объявляется новым.

- **CAP-8**
  - **intent:** Владелец создаёт самостоятельные обои, виджеты и комплекты через один проектный скилл, переиспользуя общие функции.
  - **success:** Widget: brief → HTML S/M/L → AP-HTML. Wallpaper: brief → image → AP-IMAGE → HTML фаз/эффектов → AP-HTML. Set объединяет применимые стадии частей. Затем Android → каталог → emulator → APK владельцу. Первый виджет и второй реальный цикл подтверждают процесс; технический rehearsal не входной gate. Навык/шаблоны фиксируют provenance/revisions, ограничения HTML/native и длительность с ожиданиями; превышение суток не сокращает замысел и не прекращает работу без отмены.

- **CAP-9**
  - **intent:** Владелец проверяет интерес и удержание и принимает решение о развитии на основе фактических данных.
  - **success:** Пилот 5–7 внешних участников/7 дней и отдельное 20-дневное публичное окно имеют согласованный до сбора протокол. App installs, wallpaper uses, widget instances и самоотчёты разделены; SM-1: ≥100 app installs за 20 дней, новые пороги SM-5…7 не унаследованы от Wear; GO/ITERATE/NO-GO учитывает countermetrics и стоимость поддержки.

- **CAP-10**
  - **intent:** Пользователь размещает авторские часы S/M/L, сочетает несколько экземпляров разных наборов и открывает выбранное приложение часов.
  - **success:** S показывает время, M/L время+дату; время актуально без hub, учитывает locale/12–24/timezone. Pin/picker/config/cancel/restore/update/delete не смешивают экземпляры; target unavailable даёт повторный выбор. HOME проверен, LOCK исследован и реализован только при подтверждённом host, с системной аутентификацией.

- **CAP-11**
  - **intent:** Пользователь получает стартовую коллекцию оригинальных принятых комплектов.
  - **success:** Каждый public продукт содержит заявленные поверхности, их применимые approvals и native verification: отдельные обои, часы S/M/L либо комплект. Preview соответствует точной ревизии; Контур и fixtures отсутствуют в release. Десять тем — ориентир, first-release count определяется принятыми готовыми комплектами.

## Constraints

- Все комплекты первой поставки бесплатны и встроены в обновляемое phone-приложение. Wallpaper/widget можно использовать раздельно, смешивать A/B и размещать несколько экземпляров; самостоятельный выпуск каждой части разрешён.
- Wear OS исключён решением владельца от 2026-09-27; CAP-4/CAP-5 и их IDs сохранены только в companion истории. «Контур» удалён; его старый release-ready статус остаётся историческим.
- Для присутствующих новых обоев обязательны четыре художественные фазы, движение объектов, authored уровни и отдельные tap/swipe controls; power/reduced/off может ограничить default Full. Расписание/ownership/host paths и shared schemas задают AD-1…26 и phone contracts.
- Hub имеет свои tokens/компоненты/motion; artwork проходит применимые согласования конкретных ревизий: image+HTML для обоев, HTML для часов. Ни PNG, ни HTML, ни план не считаются native acceptance.
- Собственные preferences типизированы и локальны; browsing, wallpaper и per-instance widget конфигурации разделены. Platform facts обновляются; UNKNOWN и возврат из системы не доказательство результата.
- Сохранены NFR-1…8, включая оригинальность, offline, 48dp UX, accessibility, phone performance/energy и Pixel/Samsung/HONOR baseline. Widget/combined budgets задаются до замеров. Физический результат и store route нельзя закрывать эмулятором.
- Репозиторий владеет contracts, кодом и evidence; Obsidian — portfolio view. Публикация, art/HTML approvals и принятие будущих metric thresholds остаются отдельными решениями владельца.

## Non-goals

- Wear в этой поставке, runtime phone-watch sync, Watch Face Push, AOD/health/complications виджета, другие платформы.
- Marketplace, backend, аккаунты, оплата/подписка, пользовательский AI-конструктор, клавиатура и другие виды widgets.
- Смена системных иконок/launcher, замена штатных часов lock screen, screensaver, универсальное обещание LOCK.
- Remote theme delivery, обязательные Remote Config/push/analytics SDK, production runtime timer ради минутного обновления часов.

## Success signal

Пользователь реально применяет обои A, размещает часы B и ещё один экземпляр A, сохраняет оформление после обычного закрытия hub, reboot/update и получает актуальные фазы/время. Принятая публичная ревизия проходит свой phone release evidence; внешний пилот и 20-дневный отчёт позволяют владельцу принять GO/ITERATE/NO-GO без подмены метрик. Цели конца сентября и суток на комплект не являются результатами проверок.

## Assumptions

- PRD A-3, уточнение 17 сентября: для планирования — хотя бы один принятый самостоятельный продукт плюс остальные готовые; десять не обязательны к первому выпуску. Финальный состав фиксируется до release.

## Open Questions

- До пилота: владелец принимает пороги/деноминаторы SM-5…SM-7 и protocol; до публичного окна — событие старта и источник SM-1. Epic 15 содержит явные gates, показатели не придумываются задним числом.
- До compatibility/release claims: точная physical phone/host матрица и LOCK evidence, принятый content manifest, подписанный candidate и RuStore проверка. Это будущие acceptance inputs, не неопределённость контрактов для первой инженерной story.

## Подготовка и реализация нового процесса

[Решение 17 сентября](../../planning/sprint-change-proposal-2026-09-17.md) одобряет подготовку. Schema2 всё ещё требует пару; новый формат и автоматическая регистрация самостоятельного контента — незавершённая работа 13.3. Первый native продукт — виджет; реальная сцена 13.5 потребляет необходимые части 10.2–10.5. Скилл/документы не подтверждают native-готовность, скорость производства или выпуск.
