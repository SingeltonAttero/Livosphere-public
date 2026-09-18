# Самостоятельные живые обои

Прочитай `wallpapers/CONTRACT.md` актуального UX workspace. Исходный художественный результат — оригинальная композиция в phone context с safe zones. Для bitmap генерации/редактирования используй imagegen; сохрани материалы, provenance и конкретную revision. AP-IMAGE нужен до зависимой live-проработки по этому изображению.

После AP-IMAGE опиши четыре окружения (dawn/day/dusk/night) и карту объектов: собственное движение/геометрия, trigger, priority, длительность, stop/interrupt, уровень Subtle/Balanced/Full и reduced/off. Тестовая сцена не является художественным шаблоном для всех продуктов. Новая сцена развивается в собственном renderer/assets.

HTML позволяет независимо увидеть фазы, каждый предусмотренный эффект, уровни и остановку; tap/swipe/charging показываются только для выбранных capabilities и явно как prototype. Один pan/glow всей картинки не заменяет принятое движение объектов. Controls review не обещаются как настройки приложения.

После AP-HTML переиспользуй PhasePolicy/PhaseScheduler, WallpaperRenderLoop, owner-scoped settings и lifecycle. Устойчивый отдельный service component привязан к wallpaperId; preview B не меняет active A. Новый объектный эффект реализуется в пределах потребностей сцены; Canvas-first сохраняется до подтверждённой необходимости другого renderer.

Power/reduced/off caps и hidden stop обязательны для принятого поведения. Время — местное, фазы не зависят от Android dark theme; off сохраняет актуальную статичную фазу. Системные сигналы не считаются доступными по одному HTML.

Первый реальный wallpaper цикл — 13.5. Сначала art; необходимые 10.2–10.5 реализуются на этой сцене, их завершение проверяется до native handoff. Fixture остаётся инструментом ограниченных технических сценариев. Отдельный companion widget необязателен. Физические FPS/battery/OEM проверяются отдельно от эмуляторного APK.
