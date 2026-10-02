# TBox Launcher MOD

Форк оригинального лаунчера для ГУ Jetour Dashing

Отдельный HOME-лаунчер для головного устройства Jetour Dashing (Android 9, API 28+),
выделенный из монорепозитория TBox Monitor. Устанавливается рядом с Monitor
(`applicationId=ras.dashing.tbox.launcher`) и общается с TBox через `tbox-proxy`.

## Сборка

```bash
./gradlew assembleRuDebug      # русская debug-сборка
./gradlew assembleEnDebug      # английская debug-сборка
./gradlew assembleRuRelease    # русская release-сборка
./gradlew assembleEnRelease    # английская release-сборка
```

Нужен `local.properties` в корне (не коммитится):

```properties
sdk.dir=C:\\path\\to\\android-sdk
# Публичные ссылки Яндекс.Диска для OTA-каналов:
launcher.update.devPublicKey=https://disk.yandex.ru/d/...
launcher.update.releasePublicKey=https://disk.yandex.ru/d/...
# OAuth-токен для автозаливки OTA (только на машине сборки):
yandex.disk.oauth=...
yandex.disk.launcher.devPath=/dashing/launcher-dev
yandex.disk.launcher.releasePath=/dashing/launcher-release
```

## OTA

`python tools/build_ota_launcher.py --channel dev --upload` — собирает APK,
формирует `version.json` и заливает в папку Яндекс.Диска. Приложение проверяет
обновления в окне «О лаунчере».

## Разрешения на ГУ (выдаются один раз через adb)

```bash
adb shell pm grant ras.dashing.tbox.launcher android.permission.WRITE_SECURE_SETTINGS
adb shell pm grant ras.dashing.tbox.launcher android.permission.MANAGE_ACTIVITY_STACKS
adb shell pm grant ras.dashing.tbox.launcher android.permission.ACTIVITY_EMBEDDING
adb shell appops set ras.dashing.tbox.launcher SYSTEM_ALERT_WINDOW allow
# Кнопка «Назад» в доке работает через accessibility-сервис:
adb shell settings put secure enabled_accessibility_services ras.dashing.tbox.launcher/vad.dashing.tbox.ui.launcher.LauncherNavAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

Контекст по реализации: [docs/LAUNCHER_IMPLEMENTATION_CONTEXT_RU.md](docs/LAUNCHER_IMPLEMENTATION_CONTEXT_RU.md).
