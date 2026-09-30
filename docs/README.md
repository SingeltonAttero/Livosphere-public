# Документация Livosphere

Общие актуальные требования и дизайн хранятся вместе с кодом. Архив исходного checkout не нужен, чтобы прочитать контракт и продолжить разработку.

| Что нужно | Источник |
| --- | --- |
| Продуктовые требования | [PRD](planning/prds/prd-Livosphere-2026-08-28/prd.md), [addendum](planning/prds/prd-Livosphere-2026-08-28/addendum.md) |
| Обязательный контракт | [SPEC](specs/spec-livosphere/SPEC.md) и перечисленные в нём companions |
| Архитектура | [Краткий обзор](architecture/README.md), [Architecture Spine](planning/architecture/architecture-Livosphere-2026-09-01/ARCHITECTURE-SPINE.md), [phone contracts](planning/architecture/architecture-Livosphere-2026-09-01/phone-contracts.md) |
| Текущие экраны | [Спецификация экранов](ux/SCREENS.md), [карта UX](planning/ux-designs/ux-Livosphere-2026-08-31/DESIGN.md) |
| Редактируемый дизайн | [pen.dev source и assets](../pen-design/README.md) |
| Очередь и статусы | [Epics/stories](planning/epics.md), [единственный sprint state](development/sprint-status.yaml), [sprint plan](planning/sprint-plan-phone-2026-09-09.md) |
| Контент и принятые ревизии | [Content guide](CONTENT.md), локальные DESIGN/EXPERIENCE/approval в UX workspace, [карта Pencil-источников](development/pencil-wallpaper-source-map.md) |
| Продолжение разработки | [Handoff](HANDOFF.md), [сборка](BUILDING.md), [настройка агентов](AGENTS-SETUP.md) |
| Правила MR | [Документация и дизайн в MR](process/change-documentation.md), [tracked/ignored](REPOSITORY-POLICY.md) |

## Статус перенесённых документов

Перенос выполнен 30.09.2026 из исходного checkout `3499778`. [SOURCE-IMPORT.json](planning/SOURCE-IMPORT.json) хранит исходные пути и SHA-256 до адаптации ссылок. Сохранены требования, ID, даты и основания принятия; временные review/reconcile-файлы, сырые логи, старые Wear-макеты и альтернативные концепты вне текущих коллекций не переносились. Ссылки на такие исходные материалы сняты; это не новые обязательные зависимости.

Исходные `status: final` / `approved` относятся к плану или конкретной художественной ревизии, а не к готовности текущего native-релиза. Реализация и очередность — в единственном sprint state; новые подтверждённые факты — в HANDOFF. Противоречие между источниками показывается владельцу, не исправляется молча.

Старые H1/H4-макеты в detailed UX явно заменены коррекцией 18 сентября. Текущая навигация — Обои / Виджеты / Ещё. Pencil содержит художественные источники и экраны; `.pen` не заменяет поведенческий контракт и не является физической проверкой Android.

## Дальнейшая работа

Обновляйте канонический документ по указанному пути, а не создавайте его копию в приватном BMAD output. Новые решения — `docs/decisions/`, новые спецификации и story context — `docs/development/` или принятый workspace, художественные brief/provenance/approval — `docs/content/`. Оригинальная дата в имени папки обозначает происхождение workspace, а не запрет его дальнейшего обновления. Затронутые код, docs и рабочий дизайн входят в один MR.
