# Сборка и команды

Команды выполняются из корня репозитория на macOS или Linux. Для сборки нужны JDK 17, Python 3.9+, Make и Android SDK. В Android Studio → SDK Manager установите Platform 37, Build-Tools 36.0.0 и Platform-Tools. Первая сборка скачивает Gradle и зависимости.

На macOS скрипт ищет установленный JDK 17 и SDK в `~/Library/Android/sdk`. Если `JAVA_HOME` указывает на более новый JDK Android Studio, скрипт ищет JDK 17. На Linux используются `JAVA_HOME` и SDK из `~/Android/Sdk` либо заданных переменных. Для явного выбора:

```sh
export LIVOSPHERE_JAVA_HOME=/path/to/jdk-17
export ANDROID_SDK_ROOT=/path/to/android-sdk
make doctor
make phone
```

SDK также можно задать через `sdk.dir` в локальном `android/local.properties`. Если файл и переменные окружения указывают на разные каталоги, команда объяснит конфликт. `local.properties` не коммитится.

Результат: `android/hub/app/build/outputs/apk/debug/app-debug.apk`. Минимальная ОС — Android 10 / API 29; compileSdk 37, targetSdk 36. Ключ владельца для debug-сборки не нужен.

## Установка и проверка

```sh
./scripts/android-env.sh adb devices
make phone-install PHONE_SERIAL=DEVICE_SERIAL
make check
make scripts-check
```

`make phone-install` устанавливает уже собранный **debug APK** и после успешной установки запускает `MainActivity` на том же устройстве, без `.env` и записей подписанного кандидата. Если подключено ровно одно авторизованное устройство, `PHONE_SERIAL` можно опустить. Эмулятор подходит для разработки. Подключите физический телефон, включите USB debugging и подтвердите доступ на экране.

Ошибка подписи при обновлении означает, что установленная версия подписана другим ключом. Скрипт не удаляет приложение автоматически. Используйте отдельное тестовое устройство/профиль либо удалите старую установку осознанно: её настройки будут потеряны.

| Команда | Что делает |
| --- | --- |
| `make help` | Краткая справка |
| `make doctor` | Проверяет JDK и SDK |
| `make phone` | Проверяет ресурсы и собирает debug APK |
| `make assets-check` | Проверяет описания коллекций, ресурсы и контрольные суммы |
| `make check` | Unit-тесты, lint и проверки модулей |
| `make scripts-check` | Тесты окружения и установщиков без устройства |
| `make verify` | Сборка, проверки и проверка APK |
| `make device-check` | Instrumentation-тесты; нужно одно устройство или эмулятор |
| `make offline-smoke` | Сборка и проверки без сети; зависимости должны быть скачаны |
| `make benchmark-build` | Собирает profileable APK для измерений; замеры не запускает |
| `make release-test` | Проверяет release-инструменты на временном тестовом ключе |

Все вспомогательные скрипты находятся в `scripts/`. Обычной разработке достаточно `make phone`, `make phone-install` и `make check`. Подписанная сборка описана отдельно в [RELEASE](RELEASE.md).

## После изменения кода

Например, поменяйте текст в `android/hub/app/src/main/res/values/strings.xml`:

```sh
make phone
make phone-install PHONE_SERIAL=DEVICE_SERIAL
```

Приложение откроется автоматически; проверьте изменённый экран. Для изменения логики выполните тесты затронутого модуля, например:

```sh
./scripts/android-env.sh ./scripts/gradle.sh :hub:domain:test
```

В Android Studio откройте каталог `android/`. На Windows используйте Gradle wrapper и `adb` из Android Studio; Make и shell-скрипты рассчитаны на POSIX-среду.
