---
stepsCompleted: [step-01-validate-prerequisites, step-02-design-epics, step-03-create-stories, step-04-final-validation]
status: final
owner_approval: approved-content-process-2026-09-17
amendment_approval: approved-pencil-import-2026-09-24
---


# Livosphere — текущие Epics 8–16

## Overview

Пакет принят владельцем 9 сентября; [апрув всей миграции](approval-2026-09-09-phone-baseline.md) разрешает подготовить stories и sprint без повторных меню. Это brownfield phone-plan; код прежнего baseline переиспользуется, его acceptance не переносится. Старые Epics1–7 сохранены в архиве (исторический материал; не является зависимостью текущего документа).

Сохранены принятые ID и границы прежних восьми эпиков. Номера — идентификаторы, а порядок исполнения задают зависимости stories. После готовых 8/11/10.1 действует [решение 17 сентября](sprint-change-proposal-2026-09-17.md): **12.1/12.2 → первый виджет 13.1–13.3 → реальная сцена 13.5 с нужными 10.2–10.5 → общий хаб и оценка повторяемости → состав → выпуск**. Технический rehearsal больше не блокирует первый продукт. [Решение 24 сентября](sprint-change-proposal-2026-09-24-pencil-wallpapers.md) добавляет отдельный debug-цикл Epic 16 по десяти Pencil-наборам, не расширяя принятый публичный состав. Всего 43 stories.

## Requirements Inventory

### Functional Requirements

Полные обязательные формулировки находятся в [PRD v0.4](prds/prd-Livosphere-2026-08-28/prd.md); ниже индекс для трассировки, не альтернативная редакция требований.

- FR-1: Единая точка входа и независимые маршруты.
- FR-2: Честный анимированный preview.
- FR-3: Пропускаемое знакомство с ограниченной повторяемостью.
- FR-4: Короткий путь применения.
- FR-5: Доказуемый статус обоев.
- FR-6: Восстановление пути без ложного успеха.
- FR-7: Четыре фазы по местному времени.
- FR-8: Автоматическое снижение движения при низком заряде.
- FR-9: Тематическая реакция на зарядку.
- FR-10: Реакция на перемещение рабочего стола.
- FR-11: Реакция на тап по обоям.
- FR-12: Уникальный циферблат Wear OS — отменено решением 2026-09-27.
- FR-13: Активное представление и AOD — deferred.
- FR-14: Раздельные факты о часах — deferred.
- FR-15: Получение и активация Wear-циферблата — отменено решением 2026-09-27.
- FR-16: Раздельное управление движением и уровнями эффектов.
- FR-17: Независимое управление интерактивностью.
- FR-18: Сведения о продукте и помощь.
- FR-19: Локальное «Что нового».
- FR-20: Удалённые уведомления — deferred.
- FR-21: Доставка через обновление приложения.
- FR-22: Сохранение оформления при изменении состава.
- FR-23: Повторяемое производство контента.
- FR-24: Проверяемый отчёт об эксперименте.
- FR-25: Витрина локального контента.
- FR-26: Независимый выбор оформления.
- FR-27: Авторские виджеты часов в трёх размерах.
- FR-28: Жизненный цикл экземпляров виджета.
- FR-29: Выбор и открытие приложения часов.
- FR-30: Системное добавление и настройка виджета.
- FR-31: Проверяемая поддержка экрана блокировки.
- FR-32: Движение объектов и контракт эффектов сцены.
- FR-33: Разделение отладочного и публичного контента.
- FR-34: Художественная приёмка и состав выпуска.

### NonFunctional Requirements

- NFR-1: Минимальные разрешения и приватность.
- NFR-2: Локальная ценность без постоянного соединения.
- NFR-3: Жизненный цикл и надёжность.
- NFR-4: Батарея и производительность.
- NFR-5: Читаемость и доступность.
- NFR-6: Ненавязчивое независимое движение.
- NFR-7: Посильная поддержка.
- NFR-8: Оригинальность и происхождение контента.

### Additional Requirements

- AD-1…10: текущие surface-first/Lean Clean/UDF/TEA/Hilt/Outcome и единые repository owners сохраняются; Android details не входят в pure domain.
- AD-3/11/21/23: schema2 для прежних пар и адресное wallpaper-only расширение Story 16.2, stable IDs, per-wallpaper component, per-instance settings, generated registry/resources, debug filtering и idempotent migrations.
- AD-12…19: Canvas-first и прежние численные phone budgets; min29/target36/compile37, pinned toolchain; exact-digest evidence, внешний signing, local Make, no telemetry.
- AD-20/22: один navigator, structured concurrency, explicit components/targeted intents, allowBackup=false; никаких FGS/wakelock/exact alarms/QUERY_ALL_PACKAGES.
- AD-24…26: RemoteViews host clock, S/M/L, native spike; 4 local phases/3 effects levels; pin/picker/config/restore/LOCK contracts и устойчивые pending tokens.
- Подробности source-art status и immutable candidate attestation в phone-contracts.md; source status не равен release readiness.

### UX Design Requirements

- UX-DR1: Витрина H1 показывает local allowed наборы и preview/CTA; empty не раскрывает Contour/debug.
- UX-DR2: Карточка H4 показывает только присутствующие Обои/Часы, S/M/L; один основной CTA, browsing не applied.
- UX-DR3: Примерка B при применённых A и её cancel сохраняют A и обе группы preferences.
- UX-DR4: Selected component direct preview → picker/help fallback; policy/unsupported объяснены, UNKNOWN не блокирует доступный путь.
- UX-DR5: Возврат X1 не ACTIVE; HOME/LOCK facts отдельно, fresh observation/source.
- UX-DR6: H10 pre-pin выбранная конфигурация + durable request token → callback ownID → atomic commit и initial update; нет reliance на auto-config.
- UX-DR7: Picker config получает host ID без hub draft; OK только после save+initial update, cancel/reconfigure сохраняет прежнее.
- UX-DR8: No callback/return → fresh ownIDs+pending UNKNOWN; callback идемпотентен, stale не берёт browsing и не перетирает поздние правки.
- UX-DR9: H2 показывает независимые экземпляры A1/B1; edit/delete/target каждого не меняет остальные.
- UX-DR10: S=time; M/L=time+date; actual bounds, locale/12–24/timezone, native readability.
- UX-DR11: Clock target выбирается из адресных handlers; отсутствие/удаление→повторный выбор, время работает, LOCK auth системная.
- UX-DR12: LOCK имеет device/OS/host/route evidence matrix; UNKNOWN LOCK не мешает HOME.
- UX-DR13: H7 хранит Subtle/Balanced/Full default Full и отдельный tap/swipe; power/reduced cap не сбрасывает выбор, hidden rendering stopped.
- UX-DR14: Четыре local-time художественные фазы; Android dark и brightness не замена; ручной phase override только review prototype.
- UX-DR15: Hub03.1 tokens сохранены; motion 440/220/160ms, semantic state/CTA сразу, latest-wins, no replay, system/local reduced immediate.
- UX-DR16: 48dp targets/8dp spacing, 320dp/375×812/landscape/text 200%, safe areas, TalkBack/focus; одна прокрутка и CTA visible normal.
- UX-DR17: Onboarding skippable; повтор ≤1/7days только доказанные неприменённые; UNKNOWN/ACTIVE подавляют повтор.
- UX-DR18: wallpaper AP-IMAGE→AP-HTML, widget AP-HTML S/M/L; только имеющиеся поверхности, отдельные effects/phases/levels/tap/swipe/reduced; native mismatch→новый апрув.
- UX-DR19: Две контрастные fixtures без изменения shell tokens/layout; mock/art/native/evidence не подменяются.
- UX-DR20: H3/H8/H9 local settings/help/whatsnew offline; no payment/accounts/push; save failure сохраняет draft и previous value.

### FR Coverage Map

| Требование | Stories |
| --- | --- |
| FR-1 | 9.1, 9.4, 9.5, 13.3, 14.2 |
| FR-2 | 9.1, 9.2, 9.5, 10.2, 11.2, 13.2, 13.3, 13.5, 14.2 |
| FR-3 | 9.4, 9.5, 14.2, 16.2, 16.4 |
| FR-4 | 8.2, 9.2, 13.5, 14.2, 16.2, 16.4 |
| FR-5 | 8.2, 9.2, 9.3, 10.5, 14.2, 16.3, 16.4 |
| FR-6 | 8.2, 9.2, 9.3, 9.4, 14.2, 16.4 |
| FR-7 | 10.1, 10.5, 13.5, 14.2, 14.3, 16.2, 16.3, 16.4 |
| FR-8 | 10.3, 10.5, 13.5, 14.2, 14.4, 16.1, 16.3 |
| FR-9 | 10.4, 13.5, 14.2, 14.3 |
| FR-10 | 10.4, 13.5, 14.2, 14.3 |
| FR-11 | 10.4, 13.5, 14.2, 14.3 |
| FR-12 | Отменено решением 2026-09-27; исторический ID |
| FR-13 | Отменено решением 2026-09-27; исторический ID |
| FR-14 | Отменено решением 2026-09-27; исторический ID |
| FR-15 | Отменено решением 2026-09-27; исторический ID |
| FR-16 | 8.3, 9.4, 9.5, 10.3, 13.5, 14.2, 14.4 |
| FR-17 | 10.3, 10.4, 13.5, 14.2 |
| FR-18 | 9.4, 14.2, 14.5 |
| FR-19 | 9.4, 14.2, 14.5 |
| FR-20 | Deferred push; вне новой поставки |
| FR-21 | 8.1, 8.4, 8.5, 13.3, 13.4, 14.1, 14.5, 14.6 |
| FR-22 | 8.3, 9.4, 10.5, 11.4, 13.3, 14.1, 14.2, 14.5, 14.6, 16.4 |
| FR-23 | 8.1, 8.4, 8.5, 10.2, 12.1, 12.2, 12.3, 13.1, 13.2, 13.3, 13.4, 13.5, 16.1, 16.2, 16.3 |
| FR-24 | 15.1, 15.2, 15.3, 15.4 |
| FR-25 | 8.1, 8.4, 9.1, 9.5, 13.3, 13.4, 14.2, 16.2, 16.3 |
| FR-26 | 8.2, 8.3, 9.1, 9.2, 9.3, 11.3, 11.4, 13.3, 13.5, 14.2, 16.2, 16.3 |
| FR-27 | 8.1, 11.1, 11.2, 13.1, 13.2, 13.3, 14.2, 14.4 |
| FR-28 | 8.3, 9.3, 11.3, 11.4, 13.3, 14.2, 14.4 |
| FR-29 | 9.2, 11.5, 13.3, 14.2 |
| FR-30 | 9.2, 11.1, 11.3, 11.6, 13.3, 14.2 |
| FR-31 | 9.3, 11.6, 14.2 |
| FR-32 | 10.2, 10.3, 10.4, 10.5, 12.2, 13.5, 14.2, 14.3, 14.4 |
| FR-33 | 8.1, 8.4, 8.5, 9.1, 11.1, 13.3, 13.4, 14.1, 14.5, 14.6, 16.2, 16.3 |
| FR-34 | 12.1, 12.2, 12.3, 13.1, 13.2, 13.3, 13.4, 13.5, 14.1, 14.5, 14.6, 16.1 |

### UX / NFR Coverage Map

| Требование | Stories |
| --- | --- |
| UX-DR1 | 8.4, 9.1, 13.3, 13.4 |
| UX-DR2 | 9.1, 9.2, 13.3 |
| UX-DR3 | 8.2, 8.3, 9.2, 10.5, 13.5, 14.2 |
| UX-DR4 | 8.2, 9.2, 13.5 |
| UX-DR5 | 8.2, 9.2, 9.3, 14.2 |
| UX-DR6 | 9.2, 11.3, 13.3 |
| UX-DR7 | 9.2, 11.3, 13.3, 14.2 |
| UX-DR8 | 9.2, 9.3, 11.3, 13.3, 14.2 |
| UX-DR9 | 8.3, 9.3, 11.4, 13.3, 14.2 |
| UX-DR10 | 11.1, 11.2, 12.2, 13.1, 13.2, 13.3, 14.2 |
| UX-DR11 | 11.5, 13.3, 14.2 |
| UX-DR12 | 9.3, 11.6, 14.2 |
| UX-DR13 | 9.4, 10.3, 10.4, 10.5, 13.5, 14.3, 14.4 |
| UX-DR14 | 10.1, 10.2, 10.5, 12.2, 13.5 |
| UX-DR15 | 9.5 |
| UX-DR16 | 9.5, 13.3, 14.2 |
| UX-DR17 | 9.4, 14.2 |
| UX-DR18 | 10.2, 10.4, 11.1, 12.1, 12.2, 12.3, 13.1, 13.2, 13.3, 13.4, 13.5 |
| UX-DR19 | 8.4, 9.1, 9.5, 12.3, 13.3, 13.4, 13.5 |
| UX-DR20 | 9.4, 14.2, 14.5 |
| NFR-1 | 8.3, 8.4, 11.1, 11.3, 11.4, 11.5, 13.3, 14.1, 14.2, 14.5, 14.6, 15.1, 15.2, 15.3 |
| NFR-2 | 9.1, 9.4, 11.2, 13.3, 14.2 |
| NFR-3 | 8.2, 8.3, 9.2, 9.3, 10.1, 10.2, 10.3, 10.4, 10.5, 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 13.3, 13.5, 14.2, 14.3, 14.4, 14.6, 15.2, 16.2, 16.4 |
| NFR-4 | 10.1, 10.2, 10.3, 10.4, 10.5, 11.1, 13.5, 14.3, 14.4, 14.6, 15.2, 15.4, 16.2, 16.4 |
| NFR-5 | 9.1, 9.2, 9.3, 9.4, 9.5, 11.1, 11.2, 11.5, 11.6, 12.2, 13.1, 13.2, 13.3, 13.5, 14.2, 14.5, 14.6, 16.3 |
| NFR-6 | 9.4, 9.5, 10.3, 10.4, 12.2, 13.2, 13.5, 14.2 |
| NFR-7 | 8.1, 8.4, 8.5, 11.6, 12.1, 12.3, 13.3, 13.4, 13.5, 14.1, 14.4, 14.5, 14.6, 15.1, 15.2, 15.3, 15.4 |
| NFR-8 | 8.1, 8.4, 9.1, 9.5, 10.2, 12.1, 12.2, 12.3, 13.1, 13.2, 13.3, 13.4, 13.5, 14.1, 14.5, 14.6, 16.1, 16.3 |

## Epic List

- Epic 8 — Перевести продукт на наборы для телефона.
- Epic 9 — Выбирать и сочетать обои и часы.
- Epic 10 — Пользоваться живыми сценами с фазами суток.
- Epic 11 — Размещать авторские виджеты часов.
- Epic 12 — Создавать контент по повторяемому процессу.
- Epic 13 — Подготовить стартовую коллекцию.
- Epic 14 — Выпустить проверенную версию для телефона.
- Epic 15 — Проверить интерес пользователей и выбрать развитие.
- Epic 16 — Подключить десять Pencil-обоев в debug-коллекцию.

## Общая приёмка stories

Все новые stories начинают с backlog. Story-level implementation spec создаётся штатным bmad-build при выборе конкретной story; наличие этой декомпозиции не даёт всем ready-for-dev/done.

Для code story обязательны acceptance criteria, релевантные tests, build затронутых modules и native manual path при UI; evidence привязан к точной revision. Для document/content/spike story обязательны названные review/артефакты и честные limitations, а не искусственный unit test текста. Неопределённый physical/store результат остаётся UNKNOWN. Production work и коммуникация третьим лицам этим файлом не запускаются.

В каждой story перечислены prerequisites, FR/NFR/UX/AD и проверяемый выход. Номера stories сохраняют идентичность; после изменения порядка 13.4 следует новой 13.5. Все dependencies образуют DAG. Story 13.5 начинается с арта, до завершения потребляемых ею 10.2–10.5; их PASS обязателен для её native выхода, а не для художественного входа. Календарь и capacity не подменены оценками по числу строк.

## Epic 8: Перевести продукт на наборы для телефона

Владелец получает воспроизводимую основу нескольких phone-наборов; пользователь безопасно примеряет выбранный компонент.

### Story 8.1: Описать и валидировать телефонные комплекты

As a создатель наборов,
I want проверять полный phone manifest до сборки,
So that ошибочные ресурсы и идентификаторы не попадали пользователю.

**Зависимости:** принятый baseline; нет зависимости от других stories.

**Трассировка:** FR-21, FR-23, FR-25, FR-27, FR-33, NFR-7, NFR-8, AD-3, AD-21, AD-23.

**Acceptance Criteria:**

- **AC-8.1-1 — Given** Существуют schema1 Контур и schema2 fixture; **When** валидируется состав; **Then** schema1 доступен только legacy debug; schema2 требует wallpaper, clockWidget S/M/L, previews, четыре phase refs, effects refs и provenance.
- **AC-8.1-2 — Given** Manifest содержит duplicate ID, missing ref, неизвестную schema или неверный hash; **When** запускается validator; **Then** сборка отклоняется с точным полем/файлом; не создаётся fake WFF или второй ручной registry.
- **AC-8.1-3 — Given** Новые clock layouts ещё не реализованы; **When** проверяется контракт; **Then** schema tests используют явно тестовые декларации; это не native widget PASS и не публичный контент.

**Проверки и выход:** core/contract + build-logic tests; debug build; schema1 compatibility fixtures.

### Story 8.2: Разделить системные компоненты обоев

As a пользователь,
I want примерять другие обои без изменения текущих,
So that безопасно выбирать оформление.

**Зависимости:** 8.1.

**Трассировка:** FR-4, FR-5, FR-6, FR-26, NFR-3, UX-DR3, UX-DR4, UX-DR5, AD-11, AD-23, AD-26.

**Acceptance Criteria:**

- **AC-8.2-1 — Given** Применён component A и доступен debug fixture B; **When** открывается системная примерка B; **Then** B определяется component identity; Engine/preferences A остаются прежними.
- **AC-8.2-2 — Given** Preview B закрыт отменой либо процесс пересоздан; **When** service cold-start после unlock; **Then** выбор восстанавливается по component и owned preferences, без hub Activity и global selectedSet.
- **AC-8.2-3 — Given** Direct handler отсутствует или policy запрещает действие; **When** запрошена примерка; **Then** typed result предлагает доступный picker/help либо объясняет запрет; возврат не ACTIVE.

**Проверки и выход:** wallpaper engine/domain tests; phone build; system preview A/B/cancel on emulator.

### Story 8.3: Изолировать настройки поверхностей и выполнить миграцию

As a пользователь,
I want сохранять оформление при переходе на новую версию,
So that выбор витрины и соседние виджеты не меняли мои настройки.

**Зависимости:** 8.1.

**Трассировка:** FR-16, FR-22, FR-26, FR-28, NFR-1, NFR-3, UX-DR3, UX-DR9, AD-5, AD-7, AD-23.

**Acceptance Criteria:**

- **AC-8.3-1 — Given** Есть настройки старого baseline; **When** выполняется versioned migration; **Then** debug Контур сохраняет свои values; migration атомарна/idempotent; browsing, wallpaperId и appWidgetId имеют отдельных owners.
- **AC-8.3-2 — Given** Reference отсутствует в новом release или storage повреждён; **When** читается состояние; **Then** typed failure/needs-configuration объяснены; другая тема не выбирается автоматически, остальные instances сохранены.
- **AC-8.3-3 — Given** Два writers меняют разные экземпляры; **When** transactions пересекаются; **Then** оба изменения сохранены через один repository writer; platform active/host facts не становятся persistent truth.

**Проверки и выход:** repository/domain migration tests; phone build; process recreation smoke.

### Story 8.4: Собирать выбранные наборы с безопасной фильтрацией

As a владелец продукта,
I want получать точный состав debug и release-кандидата,
So that отладочный контент не оказался в магазине.

**Зависимости:** 8.1, 8.2, 8.3.

**Трассировка:** FR-21, FR-23, FR-25, FR-33, NFR-1, NFR-7, NFR-8, UX-DR1, UX-DR19, AD-3, AD-4, AD-18, AD-21.

**Acceptance Criteria:**

- **AC-8.4-1 — Given** Включены Контур и контрастная fixture; **When** собирается debug; **Then** оба разрешают правильные namespaced resources/IDs; shell tokens не зависят от набора.
- **AC-8.4-2 — Given** Собирается release-variant candidate; **When** выбирается content closure; **Then** только public+HTML-approved; debug assets/previews/components/registry исключены; пустой public состав даёт явный отказ.
- **AC-8.4-3 — Given** Нет настоящего публичного комплекта; **When** проверяется negative packaging case; **Then** sentinel fixtures используются только тестом; не выпускается фальшивый public APK. Native/quality attestations остаются внешними по immutable digest.

**Проверки и выход:** build-logic packaging tests; debug APK; negative release-content audit.

### Story 8.5: Проверять телефонную сборку

As a разработчик,
I want запускать воспроизводимый phone build/check,
So that продолжать новый продукт без обязательных часов.

**Зависимости:** 8.4.

**Трассировка:** FR-21, FR-23, FR-33, NFR-7, AD-14, AD-15, AD-19.

**Acceptance Criteria:**

- **AC-8.5-1 — Given** Выбран новый phone profile; **When** запускаются doctor/check/phone/verify/offline-smoke; **Then** они не требуют WFF; pinned versions сохранены, команды documented и fail-fast.
- **AC-8.5-2 — Superseded 2026-09-27:** legacy WFF target удалён по решению владельца; прежнее evidence остаётся историческим.
- **AC-8.5-3 — Given** Старая release readiness команда ещё ожидает Wear; **When** пользователь читает инструкции; **Then** указан planned Epic14 profile; gate не обходится подстановкой PASS/удалением неизвестных результатов.

**Проверки и выход:** focused make/Gradle smoke, phone unit/lint/build; legacy targets discovery.


## Epic 9: Выбирать и сочетать обои и часы

Пользователь выбирает обои и виджеты в двух отдельных каталогах и применяет их независимыми системными маршрутами. [Уточнение владельца 18 сентября](decision-2026-09-18-separate-install-flows.md) заменяет прежнюю вкладку «Наборы» и единую карточку поверхностей.

### Story 9.1: Выбирать встроенные наборы в витрине

As a пользователь,
I want видеть и выбирать доступное оформление,
So that находить понравившийся комплект.

**Зависимости:** 8.5, 11.2, принятый native-контент и маршрут добавления из 13.3. Отдельный AC-13.3-2 про widget-only packaging не блокирует текущие consumer flow.

**Трассировка:** FR-1, FR-2, FR-25, FR-26, FR-33, NFR-2, NFR-5, NFR-8, UX-DR1, UX-DR2, UX-DR19, AD-3, AD-20, AD-21, AD-23.

**Acceptance Criteria:**

- **AC-9.1-1 — Given** Есть несколько manifests допустимого variant; **When** открыты Обои или Виджеты; **Then** доступны текущие зарегистрированные обои и часы в отдельных каталогах и карточках, без общей установки; debug-only отсутствует в release registry.
- **AC-9.1-2 — Given** Просматривается B при применённых A; **When** меняется карточка/preview surface; **Then** изменяются только browsing и CTA; виджеты и обои A сохранены.
- **AC-9.1-3 — Given** Список пуст/ресурс повреждён; **When** показывается витрина; **Then** явное recoverable empty/error без подставного Контура, неподтверждённых отзывов, оплаты или сетевого каталога.

**Проверки и выход:** hub domain + Compose semantics; phone build; 375x812/manual path with two accepted previews.

### Story 9.2: Примерять обои и добавлять часы из карточки

As a пользователь,
I want применять части комплектов независимо,
So that сочетать обои A и часы B.

**Зависимости:** 9.1, 11.3, 11.5.

**Трассировка:** FR-2, FR-4, FR-5, FR-6, FR-26, FR-29, FR-30, NFR-3, NFR-5, UX-DR2, UX-DR3, UX-DR4, UX-DR5, UX-DR6, UX-DR7, UX-DR8, AD-20, AD-23, AD-26.

**Acceptance Criteria:**

- **AC-9.2-1 — Given** Выбрана присутствующая поверхность Обои или Часы; **When** нажата основная CTA; **Then** вызывается её собственный selected-ID route; preview не выдаётся за установку.
- **AC-9.2-2 — Given** Уже применены A; **When** пользователь отменяет preview B или pin/config B; **Then** A и существующие widget instances не меняются; fallback/retry доступны.
- **AC-9.2-3 — Given** Система возвращает пользователя без надёжного результата; **When** hub refresh; **Then** наблюдения обновлены с источником; нет ложного ACTIVE/visible LOCK или успеха по таймауту.

**Проверки и выход:** hub gateway/domain/Compose tests; native A wallpaper+B widget+second A path.

### Story 9.3: Управлять фактами и экземплярами в Устройствах

As a пользователь,
I want видеть состояние телефонных поверхностей,
So that изменять нужный экземпляр и понимать ограничения.

**Зависимости:** 9.2, 11.4, 11.6.

**Трассировка:** FR-5, FR-6, FR-26, FR-28, FR-31, NFR-3, NFR-5, UX-DR5, UX-DR8, UX-DR9, UX-DR12, AD-5, AD-7, AD-23, AD-26.

**Acceptance Criteria:**

- **AC-9.3-1 — Given** Есть wallpaper observations и несколько widget IDs; **When** открыты Устройства; **Then** HOME/LOCK и known instance/configuration показаны отдельно; bound ID не означает visible.
- **AC-9.3-2 — Given** Изменяется/удаляется один widget; **When** выполнены config/host действия; **Then** остальные не изменены; запрос удаления через штатный host не означает, что widget уже удалён.
- **AC-9.3-3 — Given** LOCK UNKNOWN или отсутствует host; **When** нужны часы на HOME; **Then** доступный HOME путь сохранён; ограничение LOCK видно по запросу, Wear не требуется.

**Проверки и выход:** domain state/Compose tests; add/edit/delete/recreate native path.

### Story 9.4: Обновить знакомство настройки и локальную помощь

As a пользователь,
I want быстро начать и получить помощь по конкретной проблеме,
So that пользоваться оформлением без навязчивого обучения.

**Зависимости:** 9.3.

**Трассировка:** FR-1, FR-3, FR-6, FR-16, FR-18, FR-19, FR-22, NFR-2, NFR-5, NFR-6, UX-DR13, UX-DR17, UX-DR20, AD-5, AD-7, AD-20.

**Acceptance Criteria:**

- **AC-9.4-1 — Given** Первый запуск или допустимый repeat; **When** показано знакомство; **Then** Skip/CTA немедленны; повтор не чаще одного раза в 7 дней и только для обоев, которые подтверждённо не применены; ACTIVE/UNKNOWN подавляют повтор, session reset не обходит лимит.
- **AC-9.4-2 — Given** Изменены эффекты/настройки; **When** save падает или пользователь отменяет; **Then** сохранён предыдущий value и доступен draft/retry; controls неподдержанного сигнала отсутствуют либо объяснены.
- **AC-9.4-3 — Given** Приложение обновлено или offline; **When** открыты Что нового/Помощь/О продукте; **Then** версия/локальные изменения актуальны, settings/onboarding сохранены; нет network/push/account зависимости. Для подтверждённого ручного fallback помощь содержит конкретные шаги и визуальную подсказку.

**Проверки и выход:** onboarding 7-day policy tests, save failures, navigation restore; phone build/manual.

### Story 9.5: Проверить адаптивность и независимое движение хаба

As a пользователь,
I want читать и нажимать элементы на моём экране,
So that не терять путь из-за масштаба текста или motion.

**Зависимости:** 9.4.

**Трассировка:** FR-1, FR-2, FR-3, FR-16, FR-25, NFR-5, NFR-6, NFR-8, UX-DR15, UX-DR16, UX-DR19, AD-16, AD-20, AD-21.

**Acceptance Criteria:**

- **AC-9.5-1 — Given** Обычный viewport 375x812 или 320dp/landscape/text 200%; **When** открыт каждый H route; **Then** CTA visible normal, одна допустимая прокрутка на тесном экране; ≥48dp targets/8dp gaps/safe areas, focus/Back не теряются.
- **AC-9.5-2 — Given** Пользователь быстро меняет выбор или включает reduce; **When** происходит смена страницы/выбора; **Then** semantic selection/CTA обновляются сразу; latest wins, эффект прерывается, reduced immediate, return без replay.
- **AC-9.5-3 — Given** Показаны два визуально разных принятых preview; **When** сравниваются отдельные экраны; **Then** shell tokens/layout не меняются; contrast/TalkBack проверены отдельно от красоты арта. Planning HTML не считается native PASS.

**Проверки и выход:** Compose layout/semantics; build; native 320dp/landscape/text 200%/TalkBack and reduced paths.


## Epic 10: Пользоваться живыми сценами с фазами суток

Пользователь получает автономную сцену с четырьмя фазами, движением объектов и управляемыми эффектами. 10.1 уже проверена на fixture. Работы 10.2–10.5 выполняются на принятой реальной сцене цикла 13.5, после первого авторского виджета; новый универсальный engine заранее не строится.

### Story 10.1: Показывать четыре фазы по времени телефона

As a пользователь,
I want видеть подходящее времени окружение,
So that сцена менялась в течение дня.

**Зависимости:** 8.5.

**Трассировка:** FR-7, NFR-3, NFR-4, UX-DR14, AD-11, AD-25.

**Acceptance Criteria:**

- **AC-10.1-1 — Given** Local time задан injected Clock/ZoneId; **When** пересечены 05:00/08:00/18:00/21:00; **Then** выбраны dawn/day/dusk/night по half-open intervals; изменены authored environment assets, не только яркость.
- **AC-10.1-2 — Given** Изменены time/date/timezone либо выполнен resume/reboot; **When** сцена восстановлена; **Then** показана актуальная фаза без очереди пропущенных переходов; Android dark theme не заменяет расписание.
- **AC-10.1-3 — Given** Нет публичного арта; **When** проверяется runtime; **Then** четыре явно тестовых phase fixtures доказывают поведение; artwork готовность не заявлена.

- **AC-10.1-4 — Given** Visible surface, motion=off, нет TIME_CHANGED; **When** естественное время проходит 17:59→18:00; **Then** Engine PhaseScheduler меняет статичный фазовый кадр один раз без frame loop; hidden/destroy отменяют callback, resume пересчитывает срок.

**Проверки и выход:** PhasePolicy boundary/DST/date/timezone tests; wallpaper build/native phase scenario.

### Story 10.2: Оживить отдельные объекты сцены

As a пользователь,
I want видеть движение объектов выбранной темы,
So that получать живую композицию.

**Зависимости:** 10.1, 13.3.

**Трассировка:** FR-2, FR-23, FR-32, NFR-3, NFR-4, NFR-8, UX-DR14, UX-DR18, AD-12, AD-21, AD-25.

**Acceptance Criteria:**

- **AC-10.2-1 — Given** Определены object/effect/trigger/priority/levels/stop/reduced; **When** загружена сцена; **Then** разрешаются только declared refs; broken ref/unknown effect — validation failure.
- **AC-10.2-2 — Given** Принят HTML первой настоящей сцены в цикле 13.5; **When** наблюдаются её объекты; **Then** объекты меняют собственное состояние/геометрию; общий pan/свечение не заменяют object motion. Fixture применяется только для узких технических проверок.
- **AC-10.2-3 — Given** Surface скрыта/уничтожена; **When** идёт эффект; **Then** отрисовка и callbacks остановлены, ресурсы освобождены; нет frame queue после возврата.

**Проверки и выход:** renderer deterministic frames/motion contract tests; build/native object demo.

### Story 10.3: Выбирать насыщенность с безопасным снижением движения

As a пользователь,
I want выбирать количество эффектов,
So that сохранять комфорт и заряд.

**Зависимости:** 10.2.

**Трассировка:** FR-8, FR-16, FR-17, FR-32, NFR-3, NFR-4, NFR-6, UX-DR13, AD-7, AD-13, AD-25.

**Acceptance Criteria:**

- **AC-10.3-1 — Given** Новый набор содержит три authored уровня; **When** настройки не менялись; **Then** Full default; Subtle⊆Balanced⊆Full, изменения действительно меняют effects.
- **AC-10.3-2 — Given** Saver включён, battery≤20% или сигнал UNKNOWN; **When** вычисляется effective motion; **Then** reduced-safe cap соблюдён; выход только saver off+≥25%, пользовательский Full не стирается.
- **AC-10.3-3 — Given** Выбран off или system/local reduced; **When** меняется фаза/интерактивность; **Then** off сохраняет статичный актуальный кадр; reduced допускает только authored reduced set, cap нельзя обойти trigger.

**Проверки и выход:** effect-set/reducer/power hysteresis tests; phone build/manual normal-reduced-off.

### Story 10.4: Реагировать на tap swipe и зарядку по возможностям темы

As a пользователь,
I want взаимодействовать с установленными обоями,
So that видеть уместную реакцию без помех телефону.

**Зависимости:** 10.3.

**Трассировка:** FR-9, FR-10, FR-11, FR-17, FR-32, NFR-3, NFR-4, NFR-6, UX-DR13, UX-DR18, AD-11, AD-13, AD-25.

**Acceptance Criteria:**

- **AC-10.4-1 — Given** Declared tap/offset/charging capability подтверждена; **When** приходит соответствующий сигнал; **Then** эффект выполняется по карте приоритетов и завершения, не перехватывает launcher navigation.
- **AC-10.4-2 — Given** interactionsEnabled выключен; **When** приходят tap/swipe; **Then** реакций нет, фоновые разрешённые эффекты продолжаются; charging не обходится этим toggle и подчиняется своему contract/cap.
- **AC-10.4-3 — Given** Один desktop, отсутствует offset, unknown charge или быстрые повторные triggers; **When** обновляется state; **Then** сцена работоспособна, unsupported не заявлен supported; нет бесконечного charging loop или накопления эффектов.

**Проверки и выход:** trigger priority/stop tests; native installed wallpaper vs launcher signals.

### Story 10.5: Подтвердить автономную работу scene runtime

As a пользователь,
I want сохранять обои после обычных системных событий,
So that не открывать хаб для восстановления.

**Зависимости:** 10.4.

**Трассировка:** FR-5, FR-7, FR-8, FR-22, FR-32, NFR-3, NFR-4, UX-DR3, UX-DR13, UX-DR14, AD-11, AD-12, AD-16.

**Acceptance Criteria:**

- **AC-10.5-1 — Given** Работают active A и preview B; **When** hub закрыт, процесс убит либо surface пересоздана; **Then** Engine независимы, prefs A сохранены; текущие time/power перечитаны без Activity.
- **AC-10.5-2 — Given** Система меняет обои вне hub; **When** hub возвращается; **Then** свежие component facts обновлены или UNKNOWN, stale ACTIVE не утверждается.
- **AC-10.5-3 — Given** Все functional сценарии пройдены на эмуляторе; **When** публикуется evidence; **Then** указаны exact APK/hash/API/scenario; physical FPS/battery остаются отдельными Story14 gates.

**Проверки и выход:** renderer/lifecycle instrumentation, phone build, reboot/post-unlock/process recreation E2E.


## Epic 11: Размещать авторские виджеты часов

Пользователь получает реальные авторские часы S/M/L с независимыми экземплярами и подтверждёнными host-путями.

### Story 11.1: Проверить native часы на debug Контуре

As a владелец продукта,
I want увидеть AppWidget feasibility до нового арта,
So that не согласовывать невоспроизводимые часы.

**Зависимости:** 8.5.

**Трассировка:** FR-27, FR-30, FR-33, NFR-1, NFR-3, NFR-4, NFR-5, UX-DR10, UX-DR18, AD-14, AD-22, AD-24, AD-26.

**Acceptance Criteria:**

- **AC-11.1-1 — Given** Debug Контур и platform RemoteViews; **When** запущены digital и минимальный analog probes; **Then** проверены время/дата, размерные bounds и API29/целевые API без FGS/timer/wakelock.
- **AC-11.1-2 — Given** Hub закрыт и меняется минута/дата/format/timezone; **When** смотрят на actual host; **Then** TextClock актуален; AnalogClock возможности/ограничения явно задокументированы. HTML/WFF не используются как доказательство.
- **AC-11.1-3 — Given** Формат/арт не воспроизводится штатным host; **When** фиксируется результат; **Then** ограничение и проверенный доступный путь записаны до сложного art; готовность generic widget-runtime не засчитана только probe.

**Проверки и выход:** debug AppWidget probe; API29 and target emulator host; evidence limitations.

### Story 11.2: Отображать часы в трёх размерных вариантах

As a пользователь,
I want размещать подходящие по размеру часы,
So that видеть время и нужную дату.

**Зависимости:** 11.1.

**Трассировка:** FR-2, FR-27, NFR-2, NFR-3, NFR-5, UX-DR10, AD-14, AD-21, AD-24.

**Acceptance Criteria:**

- **AC-11.2-1 — Given** Выбрана theme с одним проверенным clock format; **When** создаются S/M/L; **Then** S=time, M/L=time+date; targetCells 2x2/4x2/4x3 и API29/30 fallback из contract.
- **AC-11.2-2 — Given** Host меняет реальные bounds/padding или системный масштаб/locale; **When** обновлён layout; **Then** время не обрезано, дата M/L сохранена, art не перекрывает данные; geometry limitation recorded.
- **AC-11.2-3 — Given** Hub не работает, updatePeriodMillis=0; **When** наступает минута/полночь; **Then** host clock продолжает показывать актуальное значение без provider polling; source exports/provenance и tests соответствуют теме.

**Проверки и выход:** RemoteViews/provider tests; build; native S/M/L resizes/12–24/locale/timezone.

### Story 11.3: Добавлять и настраивать экземпляр через pin или picker

As a пользователь,
I want добавить выбранные часы с подтверждением,
So that получить именно свой вариант.

**Зависимости:** 11.2.

**Трассировка:** FR-26, FR-28, FR-30, NFR-1, NFR-3, UX-DR6, UX-DR7, UX-DR8, AD-7, AD-22, AD-23, AD-26.

**Acceptance Criteria:**

- **AC-11.3-1 — Given** Hub подготовил theme/size/target и durable token; **When** host подтверждает pin; **Then** ownID/provider проверены; token потреблён атомарно один раз, config commit и initial update, без auto-config assumption.
- **AC-11.3-2 — Given** Picker дал ID без hub request; **When** пользователь Save/Cancel; **Then** OK только после save+update; cancel не создаёт success и не меняет прежнюю конфигурацию.
- **AC-11.3-3 — Given** Callback отсутствует/запоздал/дублируется, процесс умер; **When** выполняется reconciliation; **Then** UNKNOWN не заменён success/failure; old callback не присваивает browsing B и не перетирает новую revision; needs-config явен.
- **AC-11.3-4 — Given** Pin unsupported/входные extras невалидны; **When** запрошено размещение; **Then** picker/help остаётся доступен; чужие IDs/component rejected, никаких произвольных intents.

**Проверки и выход:** flow/repository tests incl process death/replay; build; native pin+picker+cancel.

### Story 11.4: Сохранять независимые экземпляры и восстанавливаться

As a пользователь,
I want настраивать несколько часов отдельно,
So that изменения одного не затрагивали остальные.

**Зависимости:** 11.3, 8.3.

**Трассировка:** FR-22, FR-26, FR-28, NFR-1, NFR-3, UX-DR9, AD-7, AD-23, AD-24.

**Acceptance Criteria:**

- **AC-11.4-1 — Given** Работают минимум три экземпляра A/B разных размеров; **When** один edit/delete; **Then** остальные prefs и отображение сохранены; три — тестовый минимум, не пользовательский лимит.
- **AC-11.4-2 — Given** Пришёл onRestored mapping; **When** исходные prefs доступны; **Then** атомарный remap+PendingIntent rebuild+initial update; RESTORE_COMPLETED только после успеха.
- **AC-11.4-3 — Given** Backup данных нет или mapping invalid; **When** нужен новый ID; **Then** allowBackup=false сохранён; явное needs-config без чужого набора; pending pins между установками не переносятся.
- **AC-11.4-4 — Given** Обычный process death/reboot/app update; **When** host восстанавливается; **Then** проверены актуальное время и настройки без hub; force-stop отдельно описан как OS-limited scenario, не скрытая гарантия.

- **AC-11.4-5 — Given** Render r1 начат до reconfigure r2, затем delete/restore; **When** поздние результаты готовы в обратном порядке; **Then** per-ID RenderCoordinator публикует только latest committed revision/current generation; stale update не откатывает r2 и не оживляет удалённый ID.

**Проверки и выход:** repository remap/idempotence tests; native: 3 instances/process/update/restore simulation.

### Story 11.5: Открывать выбранное приложение часов

As a пользователь,
I want назначать действие нажатия для каждого виджета,
So that попадать в свои часы.

**Зависимости:** 11.4.

**Трассировка:** FR-29, NFR-1, NFR-3, NFR-5, UX-DR11, AD-22, AD-23, AD-26.

**Acceptance Criteria:**

- **AC-11.5-1 — Given** Доступны совместимые clock intent handlers; **When** пользователь выбирает target; **Then** выбор per-instance; targeted queries без QUERY_ALL_PACKAGES/широкой инвентаризации.
- **AC-11.5-2 — Given** По виджету нажали; **When** target доступен; **Then** unique explicit PendingIntent.getActivity ведёт в проверенный target через безопасную non-exported Activity-router с проверенным target36 BAL; arbitrary extra не выполняется.
- **AC-11.5-3 — Given** Target удалён/запрещён либо compatible apps отсутствуют; **When** по часам нажали; **Then** время продолжает работать, доступен повторный выбор/выбор позже; на LOCK требуется штатная аутентификация.

**Проверки и выход:** target resolver/router failure tests; build; native handler removal/LOCK auth.

### Story 11.6: Проверить HOME и доступный lock-screen host

As a пользователь,
I want знать где могу разместить часы,
So that использовать только реально поддержанный путь.

**Зависимости:** 11.5.

**Трассировка:** FR-30, FR-31, NFR-3, NFR-5, NFR-7, UX-DR12, AD-14, AD-16, AD-26.

**Acceptance Criteria:**

- **AC-11.6-1 — Given** Есть конкретное устройство/OS/build/host; **When** исследуется HOME/LOCK; **Then** для каждого сценария указан PASS/FAIL/UNKNOWN и evidence; версия Android/планшет не подтверждают все телефоны.
- **AC-11.6-2 — Given** Обнаружен штатный LOCK host path; **When** размещаются часы; **Then** путь реализован/проверен для S/M/L, current time и auth; нет замены системных часов/screensaver.
- **AC-11.6-3 — Given** LOCK unavailable/UNKNOWN; **When** пользователь выбирает HOME; **Then** HOME не заблокирован, ограничения честны. Unknown physical rows остаются release gates; закрытие investigation не означает universal support.

**Проверки и выход:** PW-02 host matrix; native HOME; available LOCK paths and auth; phone build.


## Epic 12: Создавать контент по повторяемому процессу

Создатель получает скилл и применимые шаблоны; повторяемость проверяется по первым реальным продуктам, без отдельного предварительного технического rehearsal.

### Story 12.1: Оформить навык создания контента

As a создатель контента,
I want запускать отдельные маршруты widget wallpaper и set,
So that получать первый реальный продукт без разработки тестовой темы.

**Зависимости:** принятый baseline; нет зависимости от других stories.

**Трассировка:** FR-23, FR-34, NFR-7, NFR-8, UX-DR18, AD-3, AD-21.

**Acceptance Criteria:**

- **AC-12.1-1 — Given** Есть идея и выбранная поверхность; **When** запущен livosphere-content; **Then** скилл выбирает widget/HTML SML, wallpaper/image→HTML либо set с применимыми approvals; затем Android/catalog/emulator/APK.
- **AC-12.1-2 — Given** Нужная художественная ревизия не принята; **When** работа дошла до зависимой стадии; **Then** скилл сохраняет checkpoint и ждёт решение; N/A отсутствующей поверхности не подменяется PASS или фиктивным approval.
- **AC-12.1-3 — Given** Есть инструкция и шаблоны; **When** завершается подготовка; **Then** структурная проверка и разбор трёх запросов записаны; скорость/повторяемость ещё не объявлены доказанными, публикация и расписание не запускаются.

**Проверки и выход:** skill quick_validate; local links; author walkthrough widget/wallpaper/new capability; без Android tests.

### Story 12.2: Подготовить применимые шаблоны контента

As a создатель контента,
I want использовать brief review и handoff для выбранной поверхности,
So that не собирать документацию заново и не создавать лишнюю пару.

**Зависимости:** 12.1.

**Трассировка:** FR-23, FR-32, FR-34, NFR-5, NFR-6, NFR-8, UX-DR10, UX-DR14, UX-DR18, AD-3, AD-21, AD-24, AD-25.

**Acceptance Criteria:**

- **AC-12.2-1 — Given** Выбрана поверхность; **When** заполняется brief; **Then** ID/revision, авторская идея, capabilities и имеющиеся части отделены от N/A; widget SML и wallpaper four-phase/object map применяются адресно.
- **AC-12.2-2 — Given** Готовится художественный HTML; **When** используется шаблон задания review; **Then** предусмотрены отдельные SML либо фазы/эффекты/stop/reduced, viewport и явная граница browser/native; конкретный HTML создаётся в цикле продукта.
- **AC-12.2-3 — Given** Готовится Android handoff; **When** заполняется шаблон; **Then** связаны approvals/assets/registration, закрытый список проверок, APK/version и ограничения; подготовленный template не считается интегрированным продуктом.

**Проверки и выход:** template fields/links и walkthrough; browser/native проверки выполняются на реальном продукте.

### Story 12.3: Подтвердить процесс на реальных продуктах

As a создатель контента,
I want сравнить первый и второй реальные циклы,
So that найти оставшиеся ручные операции и измерить повторяемость.

**Зависимости:** 13.3, 13.5.

**Трассировка:** FR-23, FR-34, NFR-7, NFR-8, UX-DR18, UX-DR19, AD-3, AD-16, AD-21.

**Acceptance Criteria:**

- **AC-12.3-1 — Given** Первый виджет и следующая сцена прошли native handoff; **When** оценивается процесс; **Then** показаны фактические reused/templates/runtime и ручные изменения; fixture rehearsal не является prerequisite продуктов.
- **AC-12.3-2 — Given** Есть timestamps стадий approvals и исправлений; **When** подводятся результаты; **Then** время ожидания и активной работы раздельно, общее время включает оба; цель суток не представлена полученным результатом без данных.
- **AC-12.3-3 — Given** Найдена повторяемая ручная операция; **When** уточняется скилл; **Then** делается ограниченное улучшение по evidence; универсальный engine и новый delivery-framework не создаются.

**Проверки и выход:** два реальных handoff, тайминг и локальные ссылки; только проверки изменённого tooling при его изменении.

## Epic 13: Подготовить стартовую коллекцию

Владелец получает принятые новые комплекты и ограниченный стартовый состав.

### Story 13.1: Согласовать замысел первого авторского виджета

As a владелец продукта,
I want выбрать самостоятельные часы для телефона,
So that получить полезный авторский продукт первым.

**Зависимости:** 12.2, 11.1.

**Трассировка:** FR-23, FR-27, FR-34, NFR-5, NFR-8, UX-DR10, UX-DR18, AD-21, AD-24.

**Acceptance Criteria:**

- **AC-13.1-1 — Given** Получены идея или референс; **When** подготовлен brief widget; **Then** зафиксированы стиль, digital/analog, время S и время/дата ML, provenance и известные native ограничения; обои не требуются.
- **AC-13.1-2 — Given** Владелец уточняет композицию; **When** вносятся изменения; **Then** сохраняются версии и выбранное направление; согласование brief не подменяет AP-HTML.
- **AC-13.1-3 — Given** Направление выбрано; **When** передаётся работа в 13.2; **Then** есть конкретный brief и ожидаемый результат SML, без разработки wallpaper runtime и фиктивного AP-IMAGE.

**Проверки и выход:** owner brief review; provenance; native-feasibility evidence из Epic11.

### Story 13.2: Согласовать HTML первого авторского виджета

As a владелец продукта,
I want посмотреть часы во всех размерах до интеграции,
So that принять внешний вид и поведение конкретной ревизии.

**Зависимости:** 13.1.

**Трассировка:** FR-2, FR-23, FR-27, FR-34, NFR-5, NFR-6, NFR-8, UX-DR10, UX-DR18, AD-21, AD-24.

**Acceptance Criteria:**

- **AC-13.2-1 — Given** Есть принятый brief; **When** создан HTML review; **Then** SML показаны отдельно на реальном viewport и светлом/тёмном фоне; время/дата читаемы, native ограничения не скрыты.
- **AC-13.2-2 — Given** Владелец просит изменения; **When** обновляется тот же prototype workspace; **Then** правки и revision прослеживаются; browser-анимация не обещается как RemoteViews capability.
- **AC-13.2-3 — Given** Владелец принимает конкретный HTML; **When** записан AP-HTML; **Then** он относится к widget SML; wallpaper/AP-IMAGE явно N/A, Android ещё не принят.

**Проверки и выход:** browser individual viewport/readability/interaction; owner AP-HTML.

### Story 13.3: Подключить первый авторский виджет и передать APK

As a владелец продукта,
I want добавить принятые самостоятельные часы из приложения,
So that получить первый реальный продукт без ожидания всех scenes и hub работ.

**Зависимости:** 13.2, 8.5, 11.6.

**Трассировка:** FR-1, FR-2, FR-21, FR-22, FR-23, FR-25, FR-26, FR-27, FR-28, FR-29, FR-30, FR-33, FR-34, NFR-1, NFR-2, NFR-3, NFR-5, NFR-7, NFR-8, UX-DR1, UX-DR2, UX-DR6, UX-DR7, UX-DR8, UX-DR9, UX-DR10, UX-DR11, UX-DR16, UX-DR18, UX-DR19, AD-3, AD-4, AD-16, AD-18, AD-21, AD-23, AD-24, AD-26.

**Acceptance Criteria:**

- **AC-13.3-1 — Given** Есть AP-HTML SML; **When** адаптирован продукт; **Then** native layout соответствует принятой ревизии; существенное расхождение возвращается владельцу до приёмки.
- **AC-13.3-2 — Given** Текущая schema2 требует пару; **When** введено версионированное расширение manifests; **Then** widget-only имеет настоящий clock/preview/approval, без dummy wallpaper; чтение schema1/2, устойчивые IDs/settings и variant guards сохранены.
- **AC-13.3-3 — Given** Новый widget подключён через manifest; **When** собрано приложение; **Then** общий catalog mapping использует declaration, не новую ветку ручного debug-списка; минимальная карточка→Добавить часы работает без вкладки отсутствующих обоев.
- **AC-13.3-4 — Given** Пройдены целевые tests/build/native add-edit-cancel и независимость экземпляров; **When** передан APK; **Then** путь/version/digest/revision и ограничения записаны; native handoff не означает physical/store release readiness.

**Проверки и выход:** закрытый план до кода: unit schema/catalog/state и прямых зависимостей; выбранный pin/config/instance native путь; phone assembleDebug; независимый Sol/Astra review по AGENTS.

### Story 13.4: Зафиксировать ограниченный стартовый состав

As a владелец продукта,
I want выбрать готовые самостоятельные продукты и комплекты,
So that выпустить ограниченную качественную коллекцию.

**Зависимости:** 9.5; результаты/evidence 12.3 и 13.5. По поручению владельца 18 сентября состав текущих шести продуктов фиксируется сейчас; незакрытые критерии процесса/эффектов остаются явными остатками и не получают PASS от выбора состава. Состав и решение (исторический материал; не является зависимостью текущего документа).

**Трассировка:** FR-21, FR-23, FR-25, FR-33, FR-34, NFR-7, NFR-8, UX-DR1, UX-DR18, UX-DR19, AD-3, AD-16, AD-18, AD-21.

**Acceptance Criteria:**

- **AC-13.4-1 — Given** Есть принятые native продукты; **When** выбирается состав; **Then** перечислены IDs/revisions, присутствующие поверхности, применимые approvals, preview и evidence; обязательной пары для каждого продукта нет.
- **AC-13.4-2 — Given** Часть продукта ещё не готова; **When** принимается состав; **Then** непринятое исключено; отдельные clocks/wallpapers допустимы, десять тем не обязательный порог и новые виды widgets не добавляются.
- **AC-13.4-3 — Given** Состав определён; **When** готовится candidate; **Then** Contour/fixtures исключены; следующий ограниченный продукт и фактические трудозатраты записаны без автоматического запуска или публикации.

**Проверки и выход:** owner content manifest; ссылочная/variant проверка выбранного состава; release gates в Epic14.

### Story 13.5: Провести второй цикл на первой авторской сцене

As a владелец продукта,
I want получить настоящие живые обои через тот же процесс,
So that проверить добавление нового продукта и развивать runtime по реальной потребности.

**Зависимости:** 13.3, 12.2, 10.1.

**Трассировка:** FR-2, FR-4, FR-7, FR-8, FR-9, FR-10, FR-11, FR-16, FR-17, FR-23, FR-26, FR-32, FR-34, NFR-3, NFR-4, NFR-5, NFR-6, NFR-7, NFR-8, UX-DR3, UX-DR4, UX-DR13, UX-DR14, UX-DR18, UX-DR19, AD-3, AD-11, AD-12, AD-13, AD-16, AD-21, AD-25.

**Acceptance Criteria:**

- **AC-13.5-1 — Given** Первый виджет уже передан; **When** начат wallpaper цикл; **Then** приняты image и затем HTML конкретной сцены с четырьмя фазами и object/effect map; обои могут быть самостоятельными, companion clock не обязателен.
- **AC-13.5-2 — Given** Авторское поведение принято; **When** реализуются необходимые 10.2–10.5; **Then** общие lifecycle/time/power переиспользованы; runtime расширяется для выбранных эффектов, fixture остаётся только узкой проверкой.
- **AC-13.5-3 — Given** Сцена и применимые 10.2–10.5 завершены; **When** собран второй продукт; **Then** его регистрация, preview, native применение и независимость от первого виджета проверены; APK и фактическое время переданы без заявления физического PASS.

**Проверки и выход:** AP-IMAGE/AP-HTML; целевые проверки 10.2–10.5 на этой сцене; selected integration/build/native handoff; не повторять неизменённый suite.


## Epic 14: Выпустить проверенную версию для телефона

Пользователь получает проверенный phone-релиз через подтверждённый магазинный путь.

### Story 14.0: Обновить launcher-иконку хаба из pen.dev

As a владелец продукта,
I want внедрить нарезанный в pen.dev комплект launcher-иконки «Живой мир»,
So that приложение и будущий store-listing используют актуальный знак.

**Зависимости:** нет (hub-ассет, независим от release candidate).

**Трассировка:** UX-DR20, AD-18 (store/брендинг материалы).

**Acceptance Criteria:**

- **AC-14.0-1 — Given** Владелец нарезал комплект в pen.dev (`03 · Android / Полный комплект иконки приложения`); **When** внедрены ресурсы; **Then** adaptive foreground (прозрачный арт `generated-11.webp`, фрейм RpPdv), background `#100D29` (Kd1TT), monochrome вектор path kh3bq (EomWv), legacy square/round mdpi–xxxhdpi из полного арта `generated-14.png`; provenance обновлён.
- **AC-14.0-2 — Given** Изменены ресурсы иконки; **When** собран debug APK; **Then** assembleDebug SUCCESSFUL, adaptive XML + foreground PNG + legacy PNG присутствуют в APK; нет ссылок на удалённые drawable.

**Проверки и выход:** сборка `:hub:app:assembleDebug`, наличие иконок в APK, независимое review diff (Sol — APPROVE), обновление provenance.

**Фактическое выполнение:** 2026-09-25, checkpoint `checkpoint-hub-icon-update-2026-09-25.md`, коммиты `c9da10d`, `825b105`. Review Sol: APPROVE, blockers нет. Ручная приёмка владельца на устройстве подтверждена («иконка норм»).

### Story 14.1: Собрать неизменяемый phone release candidate

As a владелец продукта,
I want получить воспроизводимый подписанный кандидат,
So that проверки относились к точной будущей поставке.

**Зависимости:** 13.4.

**Трассировка:** FR-21, FR-22, FR-33, FR-34, NFR-1, NFR-7, NFR-8, AD-4, AD-15, AD-18, AD-19, AD-21.

**Acceptance Criteria:**

- **AC-14.1-1 — Given** Есть public+HTML-approved состав и внешний signing input; **When** собран release-variant candidate; **Then** получены immutable APK/AAB и generated manifest с hashes/versions/revisions; debug и WFF исключены.
- **AC-14.1-2 — Given** Native/physical/store evidence ещё отсутствует; **When** readiness вычисляется; **Then** candidate можно собрать, но публикация NOT_READY; внешние attestation records не требуют изменить APK ради статуса.
- **AC-14.1-3 — Given** Подпись временная/ключ отсутствует или evidence другого digest; **When** вызван release gate; **Then** явный отказ публикации; секреты не логируются/не коммитятся, legacy gates отдельно.

**Проверки и выход:** release profile/validator tests, signed build metadata/hash/negative gates.

### Story 14.2: Проверить функциональные пути на выпускном кандидате

As a пользователь,
I want сохранять оформление в ежедневном использовании,
So that не сталкиваться с потерей выбора и ложным успехом.

**Зависимости:** 14.1.

**Трассировка:** FR-1, FR-2, FR-3, FR-4, FR-5, FR-6, FR-7, FR-8, FR-9, FR-10, FR-11, FR-16, FR-17, FR-18, FR-19, FR-22, FR-25, FR-26, FR-27, FR-28, FR-29, FR-30, FR-31, FR-32, NFR-1, NFR-2, NFR-3, NFR-5, NFR-6, UX-DR3, UX-DR5, UX-DR7, UX-DR8, UX-DR9, UX-DR10, UX-DR11, UX-DR12, UX-DR16, UX-DR17, UX-DR20, AD-16, AD-22, AD-26.

**Acceptance Criteria:**

- **AC-14.2-1 — Given** Exact release candidate установлен; **When** пройдены wallpaperA+clockB+secondA/update/cancel/reboot/restore paths; **Then** нет reproducible crash/ANR, потери выбора или false success; scope каждого результата отмечен.
- **AC-14.2-2 — Given** На physical Pixel/Samsung/HONOR проверяются доступные hosts; **When** собирается matrix; **Then** model/OS/build/launcher и HOME/LOCK evidence явны; missing physical rows UNKNOWN не emulator PASS.
- **AC-14.2-3 — Given** Проверяются TalkBack/large text/offline/reduced; **When** наблюдаются native screens; **Then** читаемость, focus, touch и сохранение controls подтверждены; непроверенное не публикуется как support.

**Проверки и выход:** relevant unit/device suites + native phone smoke on exact candidate; dependency inventory.

### Story 14.3: Подтвердить физическую производительность обоев

As a пользователь,
I want получать плавные обои без зависаний,
So that оформление не мешало телефону.

**Зависимости:** 14.2.

**Трассировка:** FR-7, FR-9, FR-10, FR-11, FR-32, NFR-3, NFR-4, UX-DR13, AD-12, AD-13, AD-16.

**Acceptance Criteria:**

- **AC-14.3-1 — Given** Протокол наблюдаемости phone frames утверждён и привязан к APK; **When** измеряется реальная сцена; **Then** 30 fps; ≥95% intervals ≤34 ms; P99 ≤50 ms; warm-stall ≤100 ms; tap/swipe P95 ≤100 ms проверены по raw evidence.
- **AC-14.3-2 — Given** Surface hidden/resume/тяжёлая фаза; **When** повторяется сценарий; **Then** отсутствует невидимая работа/очередь; thermal/memory ограничения и конкретный device записаны.
- **AC-14.3-3 — Given** Метод не наблюдает нужные frames или данные шумные; **When** рассчитывается verdict; **Then** UNKNOWN и причина, не PASS по FPS overlay/субъективной плавности.

**Проверки и выход:** SP-06 revised protocol, Perfetto/real-device repeated scenarios on exact candidate.

### Story 14.4: Подтвердить батарею обоев виджетов и сочетания

As a пользователь,
I want сохранять приемлемый расход заряда,
So that не удалять оформление из-за батареи.

**Зависимости:** 14.3.

**Трассировка:** FR-8, FR-16, FR-27, FR-28, FR-32, NFR-3, NFR-4, NFR-7, UX-DR13, AD-13, AD-16, AD-24.

**Acceptance Criteria:**

- **AC-14.4-1 — Given** Зафиксированы режимы/brightness/network/duration и baseline; **When** измеряются normal/reduced обои; **Then** delta≤10%/≤5%; реальные trace/energy данные сравнимы, power hysteresis и UNKNOWN cap проверены.
- **AC-14.4-2 — Given** Есть widgets-only и combined сценарии; **When** выполнены ≥3 сопоставимые пары; **Then** PW-03: widgets-only ≤5%; combined normal ≤15%; combined reduced ≤10% проверены отдельно, не ослабляя wallpaper-only.
- **AC-14.4-3 — Given** Нет точного artifact/protocol или шум мешает выводу; **When** подготовлен отчёт; **Then** UNKNOWN/FAIL сохранены; fixtures/эмулятор не закрывают физический budget, изменение лимита требует записанного решения.

**Проверки и выход:** SP-07 + PW-03 physical paired baseline runs, artifact/protocol/raw hashes.

### Story 14.5: Подготовить актуальные материалы и проверить RuStore delivery

As a владелец продукта,
I want иметь понятную страницу и проверенный магазинный путь,
So that пользователь получил тот же комплект.

**Зависимости:** 14.4. Launcher-иконка уже обновлена в Story 14.0.

**Трассировка:** FR-18, FR-19, FR-21, FR-22, FR-33, FR-34, NFR-1, NFR-5, NFR-7, NFR-8, UX-DR20, AD-16, AD-18, AD-19.

**Acceptance Criteria:**

- **AC-14.5-1 — Given** Есть неизменяемый прошедший quality candidate; **When** готовятся store assets/privacy/version text; **Then** материалы отражают новые phone-наборы, без Wear/Контура/неподтверждённых effects; source rights и privacy сверены.
- **AC-14.5-2 — Given** Владелец разрешил нужное действие в магазине; **When** проверены upload/install/update; **Then** SP-04 phone evidence показывает реальный путь и сохранение preferences; moderation/канал не объявлены PASS заранее.
- **AC-14.5-3 — Given** Store требует изменения бинарного артефакта; **When** готовится новая revision; **Then** hash меняется, затронутые tests/device gates повторяются на новом candidate; старые attestations не переиспользуются молча.

**Проверки и выход:** store listing/privacy/screenshots audit; actual allowed upload/install/update evidence.

### Story 14.6: Принять выпуск и опубликовать разрешённую версию

As a владелец продукта,
I want принять доказанную поставку,
So that выпустить только проверенный результат.

**Зависимости:** 14.5.

**Трассировка:** FR-21, FR-22, FR-33, FR-34, NFR-1, NFR-3, NFR-4, NFR-5, NFR-7, NFR-8, AD-16, AD-18, AD-19.

**Acceptance Criteria:**

- **AC-14.6-1 — Given** Все обязательные phone-v2 gates закрыты и состав принят; **When** владелец рассматривает release bundle; **Then** видны tests, physical/OEM/LOCK ограничения, rights/signing/store state и нерешённые риски.
- **AC-14.6-2 — Given** Есть явное разрешение владельца на публикацию этого artifact; **When** выполняется публикация; **Then** в магазине проверены фактическая доступность/version и путь установки; факт отправки не равен доступности.
- **AC-14.6-3 — Given** Approval отсутствует/отклонён или gate UNKNOWN; **When** достигнут плановый срок; **Then** релиз не объявляется завершённым; формируется конкретный blocker и следующий шаг, без обхода качества.

**Проверки и выход:** exact-digest readiness validation; owner publication decision + verified store state.


## Epic 15: Проверить интерес пользователей и выбрать развитие

Владелец получает фактические данные и решение о развитии.

### Story 15.1: Зафиксировать протокол нового эксперимента

As a владелец продукта,
I want заранее определить что измеряем,
So that получить интерпретируемые данные.

**Зависимости:** принятый baseline; нет зависимости от других stories.

**Трассировка:** FR-24, NFR-1, NFR-7, AD-17.

**Acceptance Criteria:**

- **AC-15.1-1 — Given** Новый продукт использует один phone artifact; **When** готовится protocol; **Then** SM-1: ≥100 app installs за 20 дней сохранён; исторические SM-2/3 не превращаются в wallpaper/widget counts.
- **AC-15.1-2 — Given** Планируется пилот; **When** определяются SM-5/6/7; **Then** source/observation/self-report/denominator/missing-data/threshold и моменты feedback заданы и приняты владельцем до сбора.
- **AC-15.1-3 — Given** Планируется публичное 20-дневное окно; **When** выбран старт; **Then** зафиксированы timezone/date/event и store semantics; SM-C1: не более 2 рабочих дней поддержки на релиз и правило двух превышений сохранены; no analytics SDK.

**Проверки и выход:** protocol/metric lineage review and explicit owner metric acceptance.

### Story 15.2: Провести семидневный внешний пилот

As a владелец продукта,
I want наблюдать самостоятельное использование,
So that отделить впечатление разработчика от внешнего спроса.

**Зависимости:** 15.1, 14.4.

**Трассировка:** FR-24, NFR-1, NFR-3, NFR-4, NFR-7, AD-16, AD-17.

**Acceptance Criteria:**

- **AC-15.2-1 — Given** Есть принятый protocol и качественно проверенный candidate; **When** добровольные 5–7 внешних участников начинают пилот; **Then** устройства/версии и согласие на участие зафиксированы; dogfooding Якова отдельно.
- **AC-15.2-2 — Given** Участники самостоятельно размещают обои/часы; **When** проходят 7 дней; **Then** собраны feedback после установки/в конце недели, appearance/use/battery/retention; отсутствие ответа не success.
- **AC-15.2-3 — Given** Нужна коммуникация участникам; **When** готовится приглашение/сбор; **Then** отправка выполняется только с отдельным разрешением владельца; шаблон не считается реальным пилотом.

**Проверки и выход:** voluntary pilot records: 5–7 participants / 7 days, source-backed feedback dataset.

### Story 15.3: Собрать двадцать дней публичных наблюдений

As a владелец продукта,
I want сравнить спрос и стоимость поддержки,
So that решать развитие по данным выпуска.

**Зависимости:** 15.1, 15.2, 14.6.

**Трассировка:** FR-24, NFR-1, NFR-7, AD-17, AD-18.

**Acceptance Criteria:**

- **AC-15.3-1 — Given** Публикация состоялась и начало окна определено; **When** проходит 20 календарных дней; **Then** отчёты магазина сохраняются с semantics, dates и source; installs не объявляются уникальными пользователями/применениями.
- **AC-15.3-2 — Given** Обрабатываются component self-reports и затраты; **When** строится отчёт; **Then** SM-5/6/7 и SM-8 сохраняют свои definitions; missing data/biased cohort и cycle waits видны.
- **AC-15.3-3 — Given** Данных недостаточно или окно не завершено; **When** подготавливается статус; **Then** нет выдуманного отчёта/GO или автоматического закрытия; ожидание реального времени обозначено.

**Проверки и выход:** store exports + observation provenance + 20-day window audit.

### Story 15.4: Принять решение о следующей итерации

As a владелец продукта,
I want оценить спрос качество и посильность поддержки,
So that выбрать дальнейшую работу.

**Зависимости:** 15.3.

**Трассировка:** FR-24, NFR-4, NFR-7, AD-17.

**Acceptance Criteria:**

- **AC-15.4-1 — Given** Есть полный отчёт и countermetrics; **When** Яков принимает решение; **Then** GO/ITERATE/NO-GO аргументирован установками, качеством/удержанием/поддержкой, без автоматического вердикта по одному числу.
- **AC-15.4-2 — Given** Два последовательных релиза превысили поддержку 2 рабочих дня; **When** обсуждается развитие; **Then** владелец выбирает корректировку поддержки/NO-GO; это не автоматическое удаление продукта.
- **AC-15.4-3 — Given** Выбрана следующая коллекция/монетизация/Remote Config/Wear; **When** планируется работа; **Then** новый scope ограничен и отдельно принят; Epics 8–15 не превращаются в бессрочную реализацию всех идей.

**Проверки и выход:** evidence-linked owner GO/ITERATE/NO-GO decision.

## Epic 16: Подключить десять Pencil-обоев в debug-коллекцию

Владелец получает десять самостоятельных обоев из [принятого Pencil-состава](sprint-change-proposal-2026-09-24-pencil-wallpapers.md) в существующем Android-каталоге. Каждый продукт показывает четыре принятых кадра по местному времени и устанавливается через системный путь живых обоев. Движение объектов, интерактивность, часы и изменение публичного состава отложены. Epic 16 не переоткрывает готовые Epics 8–10 и не блокирует Epic 14 с ранее выбранным составом.

### Story 16.1: Зафиксировать принятые кадры и происхождение

As a создатель обоев,
I want сохранить точную карту всех десяти полотен и сорока фаз,
So that Android использует только принятые владельцем кадры и воспроизводимые исходники.

**Зависимости:** решение владельца от 24 сентября; существующий content-процесс.

**Трассировка:** FR-23, FR-34, NFR-8, UX-DR18.

**Acceptance Criteria:**

- **AC-16.1-1 — Given** Pencil содержит десять выбранных наборов и review-варианты; **When** фиксируется источник; **Then** каждый самостоятельный wallpaper имеет уникальный ID, выбранную основу и четыре точных node/asset ссылки; часы, карточки и дубли не включены.
- **AC-16.1-2 — Given** Владелец принял все сорок кадров как есть; **When** сохраняются локальные approval/provenance; **Then** exact source revision и решение владельца связаны с каждым кадром, а пометки Pencil о несовпадении геометрии остаются явным известным ограничением.
- **AC-16.1-3 — Given** Подготавливаются Android exports; **When** проверяются кадры; **Then** сохранены чистые композиции и отдельные лёгкие preview, исходное соотношение сторон и карта source→Android без растяжения или подмены фазы.

**Проверки и выход:** инвентарь 10×4, существование и checksum файлов, визуальный просмотр exports/crop; Android build не требуется до native-подключения.

### Story 16.2: Поддержать самостоятельные статичные обои

As a пользователь приложения,
I want видеть и применять обои без обязательных часов,
So that выбранная сцена живёт по четырём фазам без лишних эффектов.

**Зависимости:** 16.1 для точного pilot-art; завершённые Epics 8–10.

**Трассировка:** FR-4, FR-7, FR-23, FR-25, FR-26, FR-33, NFR-3, NFR-4, UX-DR3, UX-DR14.

**Acceptance Criteria:**

- **AC-16.2-1 — Given** Manifest содержит preview и wallpaper без clock; **When** он проходит generator и variant selection; **Then** создаётся валидный wallpaper-only descriptor с четырьмя phases, service, preview, provenance и stable IDs без dummy widget/effects, а schema1/2 и public guard сохраняют прежние правила.
- **AC-16.2-2 — Given** Видимый Engine статичной сцены; **When** наступает граница суток либо меняются время, дата, timezone, visibility или процесс; **Then** выбран актуальный кадр с мгновенной перерисовкой, без постоянного render loop и таймеров невидимого Engine.
- **AC-16.2-3 — Given** A установлены, B рассматриваются в каталоге; **When** пользователь вручную выбирает фазу B или отменяет системную примерку; **Then** A и их сохранённый выбор не меняются; системное подтверждение HOME проверяется действующим механизмом, возврат в Activity не считается успехом.

**Проверки и выход:** узкие unit по manifest/typed contract/catalog/phase scheduler/static redraw и выбранный native pilot; первый содержательный diff проходит контроль Astra до тиражирования.

### Story 16.3: Подключить весь принятый Pencil-состав

As a пользователь приложения,
I want выбрать каждое из десяти полотен отдельно,
So that доступен весь принятый debug-состав без дублей старой коллекции.

**Зависимости:** 16.1, 16.2.

**Трассировка:** FR-7, FR-23, FR-25, FR-26, FR-33, NFR-5, NFR-8, UX-DR1, UX-DR2, UX-DR14.

**Acceptance Criteria:**

- **AC-16.3-1 — Given** Десять принятых полотен имеют чистые exports; **When** собирается debug variant; **Then** каждое имеет четыре корректные фазы, стабильный отдельный WallpaperService, название, preview и единственную запись в существующей витрине; прежние три обоев остаются доступными.
- **AC-16.3-2 — Given** Новые продукты пока debug-only; **When** выполняется variant selection; **Then** они доступны в debug и не появляются в public/release closure, без изменения принятого стартового состава и release gates.
- **AC-16.3-3 — Given** Фазы имеют одинаковый исходный размер и общий aspect-fill renderer; **When** очередной пак импортируется; **Then** его добавление не требует нового тестового класса, а generic validator/build проверяет ссылки, размеры и состав. Визуальный crop на физическом устройстве оценивает владелец после передачи APK; до этого он UNKNOWN.

**Проверки и выход:** 10 manifests/services, source map, generic catalog/variant checks и debug build; без отдельного UI-прогона каждого пака.

### Story 16.4: Принять интегрированный debug APK

As a владелец продукта,
I want проверить все десять обоев на Android,
So that отличать работающее приложение от дизайн-концептов и release-гипотез.

**Зависимости:** 16.3.

**Трассировка:** FR-4, FR-5, FR-6, FR-7, FR-22, NFR-3, NFR-4, UX-DR3, UX-DR4, UX-DR5.

**Acceptance Criteria:**

- **AC-16.4-1 — Given** Общий механизм проверен на пилоте и все десять объявлены в debug registry; **When** владелец запускает на своём устройстве preview/confirm/cancel выбранных обоев; **Then** HOME подтверждается только наблюдаемым фактом, отмена сохраняет прежние обои, preview B изолирован от применённых A, LOCK заявляется только при подтверждённом host. До его проверки физический результат UNKNOWN.
- **AC-16.4-2 — Given** Новые сцены статичны внутри каждой фазы; **When** generic pilot-тесты проверяют границу, time/zone/resume и hidden lifecycle; **Then** кадр сменяется без непрерывной нагрузки. Физические sleep/reboot/crop проверки проводит владелец и возвращает evidence; до этого они UNKNOWN.
- **AC-16.4-3 — Given** Выбранные generic tests, сборка и одно независимое Sol/Astra review закрыты; **When** передаётся debug APK; **Then** записаны source revision, digest, путь APK, короткий маршрут проверки владельцем, инженерные результаты и ограничения. Debug handoff оставляет Story в `review` до owner device verdict и не меняет `NOT_READY` публичного выпуска.

**Проверки и выход:** закрытый список из story checkpoint до кода, generic tests на пилоте, выбранная сборка, APK/handoff и независимый review. Не писать новые тесты на каждый пак; устройство проверяет владелец.
