# Неоновая набережная

Авторская коллекция Livosphere. Базовый арт и часы приняты владельцем; нативное воплощение трёх концептов разрешено 2026-09-17. Debug preview; не public release approval. Исходный пользовательский референс служил направлением настроения, не распространяется в APK.

Четыре световых состояния: OpenAI image_gen, редактирование согласованного concept-02-harbor-v01.png. Дата 2026-09-17. Облака: отдельный прозрачный PNG image_gen, runtime NeonSceneRenderer; часы: авторские native VectorDrawable + TextClock/AnalogClock по согласованным SVG.

### morning
Edit target: the supplied accepted wallpaper. Create its morning BACKGROUND PLATE for an Android live wallpaper. Keep original vertical dimensions/aspect ratio and camera framing.
HARD INVARIANTS: preserve the EXACT woman (adult), face, hair shape/color, clothes, pose, body proportions, hands, car model shape/perspective/position, buildings skyline, branches and scene geometry. Do NOT redesign or move subjects. No extra objects. Preserve detailed cinematic anime/painterly style.
Change lighting and sky only: Early dawn: soft pale peach horizon, cool pale blue sky, soft rosy natural light on the woman, car, tree and city. Absolutely ALL artificial light sources OFF: no glowing windows, signs, lamps, headlights, tail lights, light trails, or neon reflections. Buildings and reflective water are lit only by dawn daylight.
Remove ALL CLOUDS to leave a clean natural sky gradient: moving clouds will be composited as a separate runtime layer later. Remove any visible trademarks, car badges and shoe logos while preserving material and shape. Preserve the foliage and foreground scene. No clock, text overlay, watermark, phone frame or UI. One full-bleed image, no grid or split panel.

Output: `exec-db1416b9-a8d0-4778-94b4-268b8435460a.png`

### day
Edit target: the supplied accepted wallpaper. Create its day BACKGROUND PLATE for an Android live wallpaper. Keep original vertical dimensions/aspect ratio and camera framing.
HARD INVARIANTS: preserve the EXACT woman (adult), face, hair shape/color, clothes, pose, body proportions, hands, car model shape/perspective/position, buildings skyline, branches and scene geometry. Do NOT redesign or move subjects. No extra objects. Preserve detailed cinematic anime/painterly style.
Change lighting and sky only: Full natural DAYLIGHT at noon. Clear pale blue sky, sunlit city materials, natural light on the woman's face/hair and car. Bright enough to see facades and details. Absolutely ALL artificial light sources OFF: every window, sign, lamp, headlight, tail light, and neon strip unlit; no emitted glow or colored neon reflections in water or pavement. Decorative signs are dark objects, not emitters. This is a real daytime relighting, not a brightened night filter.
Remove ALL CLOUDS to leave a clean natural sky gradient: moving clouds will be composited as a separate runtime layer later. Remove any visible trademarks, car badges and shoe logos while preserving material and shape. Preserve the foliage and foreground scene. No clock, text overlay, watermark, phone frame or UI. One full-bleed image, no grid or split panel.

Output: `exec-14d6e0d3-3663-456f-b09b-7ea5a6208ac0.png`

### evening
Edit target: the supplied accepted wallpaper. Create its evening BACKGROUND PLATE for an Android live wallpaper. Keep original vertical dimensions/aspect ratio and camera framing.
HARD INVARIANTS: preserve the EXACT woman (adult), face, hair shape/color, clothes, pose, body proportions, hands, car model shape/perspective/position, buildings skyline, branches and scene geometry. Do NOT redesign or move subjects. No extra objects. Preserve detailed cinematic anime/painterly style.
Change lighting and sky only: Warm golden-hour sunset. Clear peach/lavender gradient sky, amber natural sun light and soft mauve shadows on buildings, woman and car. Sparse subdued early-evening lights only, city predominantly lit by sunset. No luminous neon wash or broad glow. Keep the sun small at the distant horizon if already present.
Remove ALL CLOUDS to leave a clean natural sky gradient: moving clouds will be composited as a separate runtime layer later. Remove any visible trademarks, car badges and shoe logos while preserving material and shape. Preserve the foliage and foreground scene. No clock, text overlay, watermark, phone frame or UI. One full-bleed image, no grid or split panel.

Output: `exec-913935a8-d838-4980-896e-4447cd09f5c2.png`

### night
Edit target: the supplied accepted wallpaper. Create its night BACKGROUND PLATE for an Android live wallpaper. Keep original vertical dimensions/aspect ratio and camera framing.
HARD INVARIANTS: preserve the EXACT woman (adult), face, hair shape/color, clothes, pose, body proportions, hands, car model shape/perspective/position, buildings skyline, branches and scene geometry. Do NOT redesign or move subjects. No extra objects. Preserve detailed cinematic anime/painterly style.
Change lighting and sky only: Deep blue late NIGHT. Dark blue clear sky, believable cyan/magenta windows and signage in the city, local nighttime lamps and subtle car lights. Preserve the original calm cinematic look, no excessive haze. For a sunset reference, turn the sunlit sky into a dark blue night and remove the sun while keeping the SAME city and SAME composition.
Remove ALL CLOUDS to leave a clean natural sky gradient: moving clouds will be composited as a separate runtime layer later. Remove any visible trademarks, car badges and shoe logos while preserving material and shape. Preserve the foliage and foreground scene. No clock, text overlay, watermark, phone frame or UI. One full-bleed image, no grid or split panel.

Output: `exec-69df0609-dd86-4138-88a8-b976d9dd5953.png`

## Облачный слой

OpenAI image_gen, 2026-09-17; output exec-b6750794-f748-4e08-a265-4fd72be01a68.png. Задание: transparent PNG, нейтральное белое перисто-кучевое облако на широком холсте, мягкие естественные края, без неба и объектов, прозрачные поля; исходник не изменялся. Runtime придаёт оттенок текущей фазы и мягко гасит горизонтальные края для бесшовного пролёта.

## Native widget polish, 2026-09-18

Clock resource revision 2: owner-requested S/M/L geometry, inset decorations, full weekday/month and scalable analog dial. Original theme palettes retained. Native preview captures replace concept thumbnails after device verification. Visual acceptance of this correction is pending owner review.

Widget preview S/M/L v02: direct capture of the actual RemoteViews on API37, Russian locale, 2026-09-18. Matches native layouts; replaces concept rendering. Small digital variants intentionally omit overlay decoration. Wallpaper previews unchanged. Owner visual acceptance pending.
