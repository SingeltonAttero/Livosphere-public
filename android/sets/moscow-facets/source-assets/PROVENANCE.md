# Moscow Facets source provenance

Current source revision 2 follows the fixed V1 in `pen-design/Дизайн Живых обоев.pen`, verified through Pencil MCP on 2026-09-24.

- morning: `assets/generated-61.png`, Pencil node `TH15s`, unchanged from source revision 1
- day: `assets/moscow-facets/day-art-v2.png`, Pencil node `op1i8`, replaced in source revision 2
- evening: `assets/generated-55.png`, Pencil node `jcVgA`, unchanged from source revision 1
- night: `assets/moscow-facets/night-art-v3-water.png`, Pencil node `beFkR`, replaced in source revision 2
- preview: reduced PNG from hero node `m0aAXb`, whose image fill is `assets/generated-61.png`

The morning, day and evening source files are JPEG-encoded and use `.jpg` Android resource names without changing their bytes. The new night source is PNG and uses `.png`. The preview is not a runtime phase plate.


Clock source revision 3 adds the accepted Pencil V1 frames `QrBl3`, `MLeSu`, `Pa9GK` as preview-only PNGs and native S/M/L RemoteViews layouts. The bundled variable Space Grotesk TTF is the unmodified official Google Fonts file from `google/fonts/ofl/spacegrotesk` (retrieved 2026-09-24, SIL Open Font License 1.1 in `android/sets/moscow-facets/SPACE_GROTESK_OFL.txt`). Runtime time/date values remain native `TextClock`; the preview PNGs do not replace the live clock.
