---
name: Livosphere Hub
scope: hub
status: final
owner_approval: inherited-visual-baseline-and-approved-direction
owner_approval_date: 2026-08-31
owner_approval_scope: hub-visual-and-motion-baseline
version: '1.0-phone-planning'
created: 2026-08-31
updated: 2026-09-18
description: Сначала оформление и примерка; самостоятельная тёплая оболочка с лавандовым акцентом, не палитра набора.
colors:
  background: '#F8F5F0'
  surface: '#FFFFFF'
  stage: '#EAE4F3'
  text: '#2E223B'
  text-secondary: '#6B6074'
  primary: '#69428C'
  on-primary: '#FFFFFF'
  selected: '#EAE4F3'
  control-border: '#887590'
  hairline: '#D9CFDF'
  focus: '#69428C'
  error: '#B42318'
typography:
  title:
    fontFamily: 'system-ui, sans-serif'
    fontSize: 40px
    fontWeight: '650'
    lineHeight: '1.1'
  section:
    fontFamily: 'system-ui, sans-serif'
    fontSize: 20px
    fontWeight: '600'
    lineHeight: '1.3'
  body:
    fontFamily: 'system-ui, sans-serif'
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.5'
  meta:
    fontFamily: 'system-ui, sans-serif'
    fontSize: 14px
    fontWeight: '400'
    lineHeight: '1.4'
rounded:
  sm: 12px
  md: 16px
  lg: 24px
spacing:
  '1': 4px
  '2': 8px
  '3': 12px
  '4': 16px
  '5': 24px
  '6': 32px
  gutter: 20px
  touch-min: 48px
  icon: 24px
components:
  HubNavigation:
    background: '{colors.surface}'
    foreground: '{colors.text}'
    selected: '{colors.selected}'
    pageFadeDuration: 160ms
    pageOpacityFrom: '0.75'
  ProductHeader:
    background: '{colors.background}'
    foreground: '{colors.text}'
  SurfaceSelector:
    background: '{colors.surface}'
    foreground: '{colors.text}'
    radius: '{rounded.sm}'
    minimumTarget: '{spacing.touch-min}'
    selected: '{colors.selected}'
    highlightDuration: 220ms
  ArtworkStage:
    background: '{colors.stage}'
    foreground: '{colors.text}'
    radius: '{rounded.lg}'
    startupDuration: 440ms
    startupOpacityFrom: '0.45'
    startupTranslateYFrom: 6px
    startupScaleFrom: '1.015'
    surfaceSwitchDuration: 220ms
    surfaceSwitchTranslateX: 10px
    surfaceIncomingOpacityFrom: '0.25'
    surfaceIncomingScaleFrom: '0.985'
    surfaceOutgoingScaleTo: '1.015'
  TryOnAction:
    background: '{colors.surface}'
    foreground: '{colors.text}'
    radius: '{rounded.md}'
    minimumTarget: '{spacing.touch-min}'
    primaryBackground: '{colors.primary}'
    primaryForeground: '{colors.on-primary}'
    pressedDuration: 120ms
  SystemBoundary:
    background: '{colors.surface}'
    foreground: '{colors.text}'
  RecoveryPanel:
    background: '{colors.surface}'
    foreground: '{colors.text}'
  DeviceSummary:
    background: '{colors.surface}'
    foreground: '{colors.text}'
  FactDisclosure:
    background: '{colors.surface}'
    foreground: '{colors.text}'
    disclosureDuration: 180ms
  MotionChoice:
    background: '{colors.surface}'
    foreground: '{colors.text}'
    radius: '{rounded.sm}'
    minimumTarget: '{spacing.touch-min}'
    selected: '{colors.selected}'
  CapabilityNotice:
    background: '{colors.surface}'
    foreground: '{colors.text}'
  HelpDisclosure:
    background: '{colors.surface}'
    foreground: '{colors.text}'
  WhatsNew:
    background: '{colors.surface}'
    foreground: '{colors.text}'
  SetCard:
    background: '{colors.surface}'
    foreground: '{colors.text}'
    radius: '{rounded.md}'
    minimumTarget: '{spacing.touch-min}'
  WidgetSizeChoice:
    background: '{colors.surface}'
    foreground: '{colors.text}'
    selected: '{colors.selected}'
    minimumTarget: '{spacing.touch-min}'
  ClockAppChoice:
    background: '{colors.surface}'
    foreground: '{colors.text}'
    minimumTarget: '{spacing.touch-min}'
  InstanceRow:
    background: '{colors.surface}'
    foreground: '{colors.text}'
    minimumTarget: '{spacing.touch-min}'
  WallpaperEffectChoice:
    background: '{colors.surface}'
    foreground: '{colors.text}'
    selected: '{colors.selected}'
    minimumTarget: '{spacing.touch-min}'
  ReactionSwitch:
    background: '{colors.surface}'
    foreground: '{colors.text}'
    minimumTarget: '{spacing.touch-min}'
---


# Livosphere Hub — телефонные наборы

## Действующая коррекция владельца — 18 сентября 2026

Три самостоятельные вкладки **Обои · Виджеты · Ещё**. Это заменяет раннюю карусель
с информационной панелью и переключателем поверхностей на одном экране.

- Обои: изображение от края до края окна, включая фон системных баров. Внизу
  полупрозрачная «Установить обои», затем миниатюра следующей сцены. Горизонтальный свайп
  и нажатие на миниатюру меняют выбранные обои; на последней миниатюра ведёт к первой.
  «Все обои» вверху открывает общий список с миниатюрами и названиями: выбор сразу
  возвращает к нужной сцене. Back возвращает без смены выбора. Нет описаний или
  блока свойств поверх основного арта. Установка относится
  к показанному target; до его подтверждения CTA недоступен. Ошибки ведут в помощь.
- Виджеты: отдельная вертикальная LazyColumn, превью каждого авторского clock M,
  название и S/M/L. В ленте нет кнопок установки: нажатие на виджет открывает его
  карточку с крупным preview, выбором размера и действием по нажатию.
  «Установить виджет» находится только в карточке и передаёт выбранные настройки
  системному pin-пути. Другие часы выбираются в ленте. Выбор обоев и позиция ленты
  сохраняются при возврате.
- Ещё: кнопки перехода «Настройки», «Помощь с установкой», «Как установить».
  Настройки и прежний экран устройств вложены сюда. «Назад» и системный Back возвращают
  на Ещё. Нижняя навигация содержит ровно три пункта и остаётся доступной.

На обоях навигация нейтральная тёмная и прозрачная; на остальных вкладках сохраняется
светлая оболочка hub. Цвета не зависят от выбранного арта. Векторные иконки 24dp,
контролы ≥48dp; нижние отступы измеряются по фактической высоте navbar и safe insets.
Текст масштабируется системой, без декоративных автоматических циклов и заставок.

Установка обоев и часов по-прежнему требует подтверждения Android; возврат из системы
не доказывает ACTIVE. Предыдущие H1/H4 planning mocks ниже исторические.
Проверки и APK: [checkpoint](../../../../development/checkpoint-hub-carousel.md).

## Brand & Style

**Область — hub.** Визуальная основа 03.1 сохраняется: тёплый фон, лавандовое поле preview, фиолетовое главное действие и системный sans. Витрина — локальное расширение выбора до нескольких наборов. Художественные стили не перекрашивают controls; «Контур» не входит в публичную коллекцию. [Принятие и границы](approval.md).

Первый экран показывает оформление и путь к нему. Нет декоративного topbar с одним названием, диагностики перед preview, checkout, отзывов или обязательной заставки. Многотемность разрешает полезный список коллекции, но не длинный лендинг. Цвета, типографика, радиусы, spacing и motion-токены сохранены без изменения относительно 03.1.

Спины имеют приоритет над mock. [EXPERIENCE](EXPERIENCE.md) задаёт поведение и состояния; [общая карта](../DESIGN.md) — границы. Текущие иллюстрации являются planning mocks с нейтральными фикстурами, без принятого нового арта.

## Colors

| Token | Применение |
|---|---|
| `{colors.background}` | Тёплое поле хаба |
| `{colors.surface}` | Непрозрачные управляющие поверхности и подписи |
| `{colors.stage}` | Лавандовая рамка preview |
| `{colors.text}` / `{colors.text-secondary}` | Основной/вторичный текст |
| `{colors.primary}` / `{colors.on-primary}` | Одно главное действие |
| `{colors.selected}` | Выбранные набор/поверхность/размер, дополнительно отмеченные текстом/формой |
| `{colors.control-border}` / `{colors.focus}` | Смысловые границы и focus |
| `{colors.hairline}` | Декоративные разделители |
| `{colors.error}` | Конкретная ошибка действия; UNKNOWN не ошибка |

Обычный текст ≥4.5:1; крупный текст и смысловые границы/focus ≥3:1. Текст поверх художественного контента получает непрозрачную подложку. Принят светлый вид хаба, включая работу при системной тёмной теме; отдельная тёмная палитра не добавляется и не считается проверенной. Фазы обоев независимы от системной темы Android.

## Typography

`{typography.title}` — имя выбранного набора; `{typography.section}` — группа/заголовок; `{typography.body}` — действие и пояснение; `{typography.meta}` — короткие сведения. HTML px являются reference, Android наследует соответствующие роли Compose MaterialTheme.typography и масштабирует текст в sp. Шрифт арта остаётся внутри preview. При 200% подписи переносятся, targets не уменьшаются.

## Layout & Spacing

Навигация: **Наборы · Устройства · Настройки**. На витрине первым показывается выбранный/первый доступный набор с выразительным preview и главным действием; дополнительные наборы доступны в компактном списке ниже. Первая доступная тема определяется локальным release registry; не выдумывается персонализация. Категории/поиск/покупки не нужны для согласованного масштаба коллекции.

| Экран | Композиция | Иллюстрация |
|---|---|---|
| H1 — Наборы | ProductHeader выбранного/первого набора → ArtworkStage → TryOnAction «Открыть набор» → список SetCard → HubNavigation | Витрина (исторический материал; не является зависимостью текущего документа) |
| H4 — Карточка набора | Back → ProductHeader → SurfaceSelector «Обои / Часы» → ArtworkStage → один TryOnAction; короткая подсказка о независимости частей | Карточка (исторический материал; не является зависимостью текущего документа), контрастная fixture (исторический материал; не является зависимостью текущего документа) |
| H2 — Устройства | DeviceSummary обоев → список InstanceRow → CapabilityNotice HOME/LOCK; факты раскрываются по запросу | Spine-only |
| H3 — Настройки | Закрытые группы: обои выбранного набора; движение приложения; новости; помощь | Spine-only |
| H10 — Настройка часов | Название набора → preview → WidgetSizeChoice → ClockAppChoice → сохранить/продолжить и отмена | Конфигурация (исторический материал; не является зависимостью текущего документа) |
| H7 — Настройки обоев | Явное имя обоев → WallpaperEffectChoice → ReactionSwitch; при power-cap короткое объяснение | Spine-only |
| H5/H6/H8/H9 | Одна колонка контекстной помощи/фактов/локальных новостей, Back | Spine-only |

Главный CTA H1/H4 виден на 375×812 при обычном тексте. Область арта адаптируется по доступной высоте, controls не скачут при переключении поверхности/набора. Часы S/M/L показываются в художественных пропорциях соответствующего widget, а не в прежней круглой Wear-рамке.

`{spacing.1}`…`{spacing.6}`, gutter `{spacing.gutter}`, Android targets ≥48dp, промежутки ≥8dp. На 320dp, landscape и при 200% допускается одна прокрутка всей страницы; нет вложенного scroll или клиппинга содержимого. Navigation/CTA учитывают system insets и не закрывают focus. H10 при большом тексте ставит размеры вертикально; данные экземпляра не обрезаются.

## Elevation & Depth

Белые непрозрачные controls отделяются от фона тоном; stage может иметь прежнюю мягкую тень. Арт не становится фоном текста действия. Нет glow у кнопок, blur-overlay или тяжёлых слоёв вокруг списка наборов.

## Shapes

`{rounded.sm}` — selector/выбор; `{rounded.md}` — controls и карточки; `{rounded.lg}` — stage. Векторные иконки одной семьи, `{spacing.icon}`, Android hit area ≥48dp. Pressed изменяет тон/opacity, сохраняя размеры.

## Components

| Component | Визуальный контракт |
|---|---|
| HubNavigation | Три читаемые подписи; selected отмечен формой и тоном, цели неподвижны |
| ProductHeader | Имя набора и одна фраза о его оформлении; без лишнего брендингового appbar |
| SurfaceSelector | Две подписи «Обои / Часы», выбранное состояние текстовое и визуальное |
| ArtworkStage | Постоянная лавандовая область вокруг переданного арта; карточка показывает один контекст за раз |
| TryOnAction | Один фиолетовый CTA с белым текстом; secondary/отмена без конкурирующего заполнения |
| SystemBoundary | Только review: явная подпись «Плановый макет. Системный шаг не выполняется» |
| RecoveryPanel | Краткая причина, рабочий следующий шаг и возврат; без install-лендинга |
| DeviceSummary | Имя поверхности и краткий независимый факт; UNKNOWN без красного alert |
| FactDisclosure | Читаемые пары факт/сведения с переносом строк, источник и актуальность по запросу |
| MotionChoice | Подписанный выбор normal/reduced именно хаба; системный reduced имеет приоритет |
| CapabilityNotice | Объяснение конкретного ограничения; не выключенный switch без причины |
| HelpDisclosure | Помощь/версия/права по запросу; спокойная текстовая группа |
| WhatsNew | Короткие локальные сведения установленной версии, закрываемые |
| SetCard | Preview плюс имя; selected не выглядит статусом применения, нет цены и fake downloads |
| WidgetSizeChoice | S/M/L с подписями «Маленькие — время», «Средние/Крупные — время и дата»; перенос без сжатия targets |
| ClockAppChoice | Подписанная строка доступного приложения; пустое значение «Системные часы», причина fallback видима |
| InstanceRow | Набор, размер и различимый экземпляр; изменение относится к указанным часам |
| WallpaperEffectChoice | Три подписанных уровня «Сдержанно / Сбалансированно / Все эффекты»; пользовательский и эффективный уровень различимы |
| ReactionSwitch | Отдельный подписанный переключатель «Реакции на касания и свайпы», не сливается с общим уровнем |

### Motion appearance

Сохранены `{components.ArtworkStage.startupDuration}` первого появления арта, `{components.ArtworkStage.surfaceSwitchDuration}` смены preview и `{components.HubNavigation.pageFadeDuration}` смены вкладки. Все исходные opacity/scale/translation-токены остаются из 03.1. Они относятся к оболочке, а не к движению обоев/виджета.

Header, CTA и navigation доступны сразу. Первый эффект один раз за сессию; возврат из системы его не повторяет. Новый выбор отменяет прежний, значение и label обновляются немедленно. Reduced — сразу конечное состояние без движения/fade. Pressed `{components.TryOnAction.pressedDuration}` и disclosure `{components.FactDisclosure.disclosureDuration}` не меняют геометрию.

## Do's and Don'ts

| Делать | Не делать |
|---|---|
| Показать оформление и одно действие | Начинать с UNKNOWN или обязательной помощи |
| Показывать доступные локальные наборы | Возвращать debug-контент в release empty state |
| Различать выбранное и применённое | Помечать B применённым из-за открытия preview |
| Сохранять оболочку при подстановке разных art | Перекрашивать CTA в цвет темы |
| Отделять каждый widget instance | Применять глобальные настройки к A1/B1 |
| Переносить текст и разрешать одну прокрутку | Обрезать подписи/уменьшать touch targets |
| Учитывать reduced и новый выбор сразу | Блокировать действие анимацией или очередью |
| Называть planning mock и системную границу | Выдавать иллюстрацию за pin/apply/owner artwork approval |
