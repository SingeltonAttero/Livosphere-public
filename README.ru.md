# Livosphere

Авторские живые обои и часы для Android от Вебера Якова. Выбирайте оформление из локальной коллекции, устанавливайте обои и добавляйте виджеты S/M/L независимо, включая сочетания разных коллекций.

[English](README.md) · [Спецификации экранов](docs/README.md) · [Сборка](docs/BUILDING.md) · [Дизайн](pen-design/README.md)

[Политика конфиденциальности](https://singeltonattero.github.io/Livosphere-public/privacy/)

## Приложение

В текущей коллекции 13 оформлений. Есть листание и общий каталог обоев, каталог часов, карточка установки, управление экземплярами и настройки движения. Аккаунт и сеть для работы не нужны. Новые коллекции доставляются обновлением приложения.

![Обои в витрине](docs/images/01-orbital-window.jpg)

Изображение — рекламная композиция из приложения и авторского арта. [Источник](docs/images/README.md).

Публикация в RuStore находится в процессе; ссылка будет добавлена после появления карточки приложения.

## Собрать и проверить

Нужны JDK 17, Python 3.9+, Make и Android SDK Platform 37 с Build-Tools и Platform-Tools.

```sh
make doctor
make phone
./scripts/android-env.sh adb devices
make phone-install PHONE_SERIAL=DEVICE_SERIAL
make check
```

APK: `android/hub/app/build/outputs/apk/debug/app-debug.apk`. Для разработки не нужен ключ подписи владельца. Подробности окружения и устранение ошибок — в [инструкции сборки](docs/BUILDING.md), ручная проверка — в [TESTING](docs/TESTING.md).

## Разработка

Kotlin, Jetpack Compose, Hilt, DataStore, WallpaperService и AppWidget/RemoteViews. [Архитектура](docs/architecture/README.md) описывает модули, [спецификации](docs/README.md) — поведение экранов, [CONTENT](docs/CONTENT.md) — добавление ресурсов и коллекций. Все вспомогательные команды находятся в `scripts/` и `Makefile`.

## Лицензии

Код и документация проекта — [MIT](LICENSE). Авторский арт имеет [отдельные условия](ASSET-LICENSE.md). Лицензии сторонних шрифтов и инструментов сохранены в [THIRD-PARTY-NOTICES](THIRD-PARTY-NOTICES.md).
