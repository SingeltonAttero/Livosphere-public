
# Витрина-карусель — 18 сентября 2026

## Свайпы, карточка виджета и общий список обоев

Продолжение напрямую, baseline `main f8238ae`. Владелец ожидает листание обоев
влево/вправо. Вертикальный pager был неверной интерпретацией наброска и заменяется
горизонтальным. Миниатюра снизу и вертикальная лента виджетов сохраняются.

Дополнения владельца в ходе работы: у виджетов в ленте нет кнопок установки;
нажатие открывает карточку выбранных часов с preview, размерами S/M/L, действием
по нажатию и установкой. Кнопка «Все обои» открывает общий список миниатюр, откуда
можно перейти сразу к нужным обоям. Три пункта нижней навигации сохраняются.

Scope: `hub/app` — pager, каталог обоев, лента виджетов и существующий pre-pin экран;
`hub/domain` — вложенное назначение каталога; документы hub. Установка использует
существующий pin-путь, без изменения runtime/settings contracts.
Обновлённый закрытый список проверок до расширения реализации:
- `:hub:domain:test --tests app.livosphere.hub.HubReducerTest` и
  `:hub:app:testDebugUnitTest --tests app.livosphere.hub.PhoneWallpaperViewModelTest`.
- `:hub:app:assembleDebug :hub:app:assembleDebugAndroidTest`, phone profile, JDK17.
- API37: `ThemeCarouselTest`, `CarouselViewportTest`,
  `ClockWidgetConfigurationTest#prePinDetailsKeepSelectedWidgetAndInstallOnlyAfterConfirmation`.
  Свайпы в обе стороны/точный target; выбор из каталога/Back; карточка каждого
  виджета без установки из ленты; preview/настройки и pin только из карточки;
  fontScale 2.0 и прежние семь viewport-конфигураций.
- Native: горизонтальные свайпы, каталог → нужные обои → системная примерка;
  лента виджетов → карточка → размер/действие → системное подтверждение/отмена.
- `git diff --check`, тематический коммит main, перенос в dev и debug-сборка dev.
  Готово после целевых проверок и обновления APK на эмуляторе.

### Выполнение

- `HubReducerTest`: 10 PASS; `PhoneWallpaperViewModelTest`: 8 PASS.
- Debug/AndroidTest сборки PASS. Выбранный API37 прогон: 14 PASS — шесть сценариев
  карусели/каталога/навигации, семь viewport-конфигураций и настройка всех трёх
  виджетов с восстановлением размера после пересоздания Activity.
- Native API37, emulator-5554, 1080×2400 / 420dpi: свайпы влево/вправо работают;
  список открывает «Ночную сакуру», системный Preview подтверждает её название.
  В ленте часов нет CTA установки; карточка показывает выбранные часы, S/M/L меняют
  preview. Размер L и действие Clock переданы системному добавлению: показан
  «Livosphere: крупные часы», 4×3. Подтверждение отменено, новый экземпляр не добавлен.
- Крупный текст 2.0: настройки и установка достижимы прокруткой карточки;
  landscape 2.0: «Все обои», установка, миниатюра и navbar не перекрываются.
  Font/rotation восстановлены, итоговая сборка открыта в приложении.
- Артефакты: `build/hub-browsing/Livosphere-hub-browsing-debug.apk`,
  `wallpaper-catalog.png`, `widget-detail-M.png`, `widget-feed.png`,
  `native-review.json` и `hub-browsing-*.log` в той же директории.

## Коррекция владельца: обои, виджеты, служебная вкладка

Продолжение той же задачи напрямую, baseline `ea7a171`. Предыдущая композиция ниже
заменена новым поручением и наброском: полноэкранный арт без информационной панели,
полупрозрачная «Установить обои», миниатюра следующей сцены снизу. Самостоятельная
вкладка «Виджеты» — вертикальная лента с установкой конкретных часов. Последняя
вкладка «Ещё» содержит переходы к настройкам и помощи/состоянию установки; настройки
открываются отдельным экраном с возвратом. Три пункта нижней навигации.

Затронуты `hub/domain` (новые назначения и состояние поверхности), `hub/app`
(навигация, обои, лента виджетов, служебные переходы, system insets), локальные hub
DESIGN/EXPERIENCE. Арт и native wallpaper/widget runtime сохраняются.

Закрытый список проверок этой коррекции (заменяет предыдущий список для нового diff):
- `:hub:domain:test --tests app.livosphere.hub.HubReducerTest` — выбор вкладок,
  восстановление поверхности и отмена устаревшей установки при уходе с обоев.
- `:hub:app:testDebugUnitTest --tests app.livosphere.hub.PhoneWallpaperViewModelTest`.
- `:hub:app:assembleDebug :hub:app:assembleDebugAndroidTest` с JDK17, phone profile,
  `--max-workers=2`; API37 классы `ThemeCarouselTest`, `CarouselViewportTest` и метод
  `NeonCollectionTest#threeVisibleCollectionsSelectTheirOwnPreviewAndSystemTarget`,
  обновлённые под новый маршрут. Проверить next/swipe/CTA, независимую установку
  виджетов, вложенные настройки/помощь и Back, отсутствие перекрытий при fontScale2
  и в landscape. Имеющиеся 7 viewport-конфигураций сохранить.
- Native API37: обои и следующая миниатюра, системная примерка, лента/установка
  часов, Ещё → Настройки/Помощь → Back; screenshots и проверка safe areas.
- Собственный diff и `git diff --check`, APK владельцу, тематический коммит main,
  перенос в dev; после объединения — те же выбранные unit и debug-сборка.

Готово после выбранных проверок и установленного APK. Работа без делегирования;
независимое review не заявляется. Подтверждение владельца готовому виду — по показу.

### Выполнение коррекции

- `HubReducerTest`: 9 PASS; `PhoneWallpaperViewModelTest`: 8 PASS.
- Debug и AndroidTest APK собраны, phone profile / JDK17.
- API37: 13 выбранных cases PASS (5 маршрутов `ThemeCarouselTest`, 7 размеров/
  масштабов `CarouselViewportTest`, 1 метод `NeonCollectionTest`).
- Проверка возврата к прокрученной ленте выявила изменение её viewport при уходе
  на полноэкранные обои. Отступы перенесены внутрь каждого назначения, состояние
  ленты сохраняется в HubApp. После исправления повторены только затронутые
  `ThemeCarouselTest` и `CarouselViewportTest`: 12 PASS.
- Native маршрут на emulator-5554, API37, 1080×2400 / 420dpi: все три сцены,
  системный Preview выбранных обоев, настройка конкретных часов, Ещё → Настройки/
  Помощь → Back. Проверены fontScale 2.0, landscape, светлые/тёмные системные иконки.
  Настройки эмулятора восстановлены. Физическое устройство и батарея не проверялись.

Артефакты этой коррекции: `build/hub-surfaces/Livosphere-hub-surfaces-debug.apk`,
`final.png`, `widgets-final.png`, `more.png`, `native-review.json`,
`final-native-review.json`, `hub-surfaces-*.log` в той же директории.
Предыдущие `build/hub-carousel/` ниже относятся к заменённой композиции.

---

Продолжение `spec-neon-collection-android.md`, baseline `4d4b1c0`, режим напрямую.
Владелец подтвердил полноэкранную карусель, информативную карточку и кнопку
«Установить». Разрешил сохранить результат локально в dev/main; remote отсутствует,
публикация приложения не поручена. `master` в репозитории нет.

## Результат и границы

Область `hub`: три принятых изображения занимают всю доступную область витрины.
Свайп и доступные кнопки переключают набор. Название, описание, свойства и CTA
относятся к показанному набору. Обои и часы выбираются отдельно; установка открывает
системную примерку или настройку виджета с подтверждением Android. Нельзя запустить
предыдущий набор во время смены выбора. На обычном телефоне CTA виден сразу,
при крупном тексте/в landscape доступна вертикальная прокрутка.

Модули: `hub/app` (ThemeScreen/carousel, hub tokens, strings, UI tests), локальные
hub DESIGN/EXPERIENCE и этот checkpoint. Арт, renderer, AppWidget runtime и release
состав не меняются. Не создавать новые эпики, дополнительные художественные макеты
или круги аудита. Пользовательские AGENTS.md и work-mode сохраняются нетронутыми.

## Закрытый список проверок

JDK17, `android/gradlew -p android --console=plain --no-daemon --max-workers=2
-Plivosphere.buildProfile=phone`:

1. `:hub:app:testDebugUnitTest --tests app.livosphere.hub.PhoneWallpaperViewModelTest`
   — смена target и защита системного запуска от устаревшего выбора.
2. `:hub:app:assembleDebug :hub:app:assembleDebugAndroidTest`.
3. API37: `am instrument -w -e class app.livosphere.hub.theme.ThemeCarouselTest,app.livosphere.hub.theme.CarouselViewportTest,app.livosphere.NeonCollectionTest#threeVisibleCollectionsSelectTheirOwnPreviewAndSystemTarget app.livosphere.test/androidx.test.runner.AndroidJUnitRunner`.
   Свайп/кнопки, выбранный набор для CTA, независимые часы, отключённый/недоступный
   target, быстрые смены и reduced motion; 375×812, 320×568, 812×375, 800×1100,
   fontScale 1/2; штатные registry/ресурсы трёх наборов.
4. Короткий native маршрут: все три свайпом, системная примерка выбранной сцены,
   возврат, настройка её часов; screenshots обычного и крупного текста, landscape,
   системная тёмная тема (хаб сохраняет согласованную светлую оболочку).
5. Просмотр собственного diff, `git diff --check`; локальный тематический коммит
   в main, объединение main в dev с сохранением существующего Epic10.
   На объединённом dev — те же выбранные unit и сборка debug, без полного suite.

Готово: проверки выше выполнены, APK установлен и открыт на API37, результат
сохранён в обеих ветках. Независимое review не заявляется в прямом режиме.
Physical/OEM/battery/store не проверяются этим изменением.

## Выполнение

Начало: 18 сентября 2026. Код готов, выполнены выбранные проверки:

- `PhoneWallpaperViewModelTest`: 8 unit tests PASS.
- Debug APK и AndroidTest APK: сборка PASS.
- `ThemeCarouselTest`: 4 PASS — реальный HubApp с test gateway и перехваченным
  системным launcher; каждый свайп/кнопка запускает service именно показанного набора,
  возврат из настроек и часы сохраняют выбор, stale target не устанавливается.
- Выбранный метод `NeonCollectionTest`: PASS — три registry previews/services,
  недоступность прежних fixture/contour в системном выборе.
- `CarouselViewportTest`: 7 PASS — указанные размеры и fontScale 1/2. Первый прогон
  выявил округление Dp-координат в тесте; размеры проверяются с допуском половины dp
  на преобразование пикселей. В UI targets остаются 48dp. Повторён только этот класс.
- Нет автоперелистывания/вступительной анимации; активная страница одна в accessibility
  tree, выбор доступен без свайпа. Нейтральный scrim .94 обеспечивает контраст
  белого/вторичного текста поверх самых светлых участков арта. Нижняя навигация и
  safeDrawing insets сохранены. Authentication/forms/auto-rotation N/A.

Ручной native путь PASS на emulator-5554, API37, 1080×2400 / 420dpi:
все три набора свайпом; CTA «Ночная сакура» открыл системный Preview с её названием;
часы открыли native настройку S/M/L; возврат сохранил набор. Крупный текст 2.0,
landscape и системная тёмная тема просмотрены, clipping CTA не обнаружен. Настройки
fontScale/rotation/night восстановлены. После правки текста знакомства APK пересобран.

Артефакты от корня репозитория:
- `build/hub-carousel/Livosphere-hub-carousel-debug.apk` — текущая debug-сборка main.
- `build/hub-carousel/{harbor,last-light,sakura,sakura-clock}.png` — четыре экрана.
- `build/hub-carousel/{large-text-action,landscape,system-dark}.png` — визуальные проверки.
- `build/hub-carousel/native-review.json` и `carousel-*.log` — фактические native/Gradle результаты.

Собственный diff просмотрен, `git diff --check` PASS. Git-запись main и объединение
в dev завершаются после этого checkpoint; итог объединения хранится в dev.

## Интеграция dev

`main ea7a171` объединён поверх `dev e99f601`; конфликтовали только даты в README
и sprint-status. Сохранены завершённый Epic10 из dev и новый процесс/контент из main.
Код объединился автоматически; PhoneWallpaperViewModelTest (8 tests) и
`:hub:app:assembleDebug` PASS на объединённом dev, `git diff --cached --check` PASS. Владелец получил APK main, runtime-состав dev шире за счёт Epic10.

Интеграция завершена 18 сентября 2026. Remote/push не создавались; main и dev
содержат новую витрину и все три набора. dev дополнительно сохраняет Epic10.

## Интеграция коррекции в dev

`main f8238ae` объединён с `dev 88185e5` без конфликтов. Epic10 сохранён.
Выбранные задачи `HubReducerTest` (9 cases, результат переиспользован Gradle),
`PhoneWallpaperViewModelTest` (8 cases) и `:hub:app:assembleDebug` — PASS.
`git diff --cached --check` PASS. Итоговый APK main установлен и открыт на API37;
последний native проход также подтвердил сохранение прокрутки виджетов.
Лог объединённой сборки: `build/hub-surfaces/hub-surfaces-dev-build.log` в основном
checkout. Сохранение локальное; remote и push не выполнялись.

## Интеграция каталога и карточек в dev

`main ddd4ed3` объединён с `dev b17df8b` без конфликтов. Выбранная
`:hub:app:assembleDebug` на объединённом dev — PASS; `git diff --cached --check` — PASS.
Основные проверки новой реализации: 18 unit и 14 Android cases на main, ручной
API37 маршрут и системные экраны установки. Лог dev сохранён в основном checkout:
`build/hub-browsing/hub-browsing-dev-build.log`. Владелец получает APK main.
Remote/push не выполнялись, Epic10 в dev сохранён.
