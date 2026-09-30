
# Stable capability IDs и граница истории

Обновление 2026-09-09 по принятому phone-plan. CAP-1…CAP-9 сохраняют исходные ID. CAP-1/2/3/6/7/8/9 amended в SPEC; CAP-10 и CAP-11 добавлены. Номер отложенной capability нельзя присвоить новому поведению.

| ID | Исторический intent / success | Текущий статус |
| --- | --- | --- |
| CAP-4 | Читать время/дату/заряд Wear-циферблата в active/AOD с принятым акцентом; success требует WFF XML/memory и SP-02 emulator/physical evidence | Deferred Wear; не входит в Epics8–15 |
| CAP-5 | Получить и вручную активировать Wear-циферблат с раздельными compatibility/install/active facts; success требует SP-03/SP-04 route evidence без ложного ACTIVE | Deferred Wear; не заменяется AppWidget |

Прежние cap/status/AD и release evidence сохранены в архиве до миграции (исторический материал; не является зависимостью текущего документа). Прежний CAP-7 phone+WFF изменён на coordinated phone-v2 release, CAP-8 расширен на повторяемое производство комплектов, CAP-9 использует новые metric semantics с сохранением исторических IDs SM-2/SM-3.

Полные текущие PRD и UX/Architecture перечислены как adopted companions: downstream обязан прочитать их, поскольку kernel не дублирует все line-item требования. Старые review/approval reports, memlogs и generated galleries — wrapper-only документы и не становятся contract companions.
