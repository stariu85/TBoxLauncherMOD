# Standalone TBox Launcher (`:launcher`)

Отдельное Android HOME-приложение на базе Tesla-style лаунчера из TBox Monitor.

| | TBox Monitor (`:app`) | Launcher (`:launcher`) |
|--|--|--|
| `applicationId` | `vad.dashing.tbox` | `ras.dashing.tbox.launcher` |
| Назначение | Monitor + trips + fuel + HOME | HOME / 3D / climate / apps + TBox статус |
| tbox-proxy UDP | да | да (лёгкий клиент) |
| Trips / fuel / themes | да | нет |

## Сборка

```
./gradlew :launcher:assembleRuDebug
./gradlew :launcher:assembleEnDebug
```

APK: `launcher/build/outputs/apk/ru/debug/`.

## Установка рядом с Monitor

Оба APK могут стоять одновременно (разные `applicationId`). После установки выберите **TBox Launcher** как HOME на ГУ.

`tbox-proxy` владеет UDP **50047** через `TBoxBridgeService`. Если оба приложения установлены, оба подключаются к одному мосту по IPC (тот, кто поднял сервис первым, держит сокет). Monitor уже умеет работать с этим прокси.

```
adb install -r launcher/build/outputs/apk/ru/debug/launcher-ru-debug.apk
```

## OTA

Кнопка **«Настроить»** открывает экран about + обновлений.

### Публичные папки (чтение с ГУ)

В `local.properties`:

```
launcher.update.devPublicKey=https://cloud.mail.ru/public/yT4V/JC5C1FwA9
launcher.update.releasePublicKey=https://cloud.mail.ru/public/yT4V/JC5C1FwA9
```

Публичные ссылки — **только чтение**.
### Токен (один раз)

1. Создать приложение на https://oauth.yandex.ru/ (доступ к Диску: write + read).
2. Открыть в браузере: `https://oauth.yandex.ru/authorize?response_type=token&client_id=<CLIENT_ID>`
3. Скопировать `access_token` в `yandex.disk.oauth=...`

### Сборка + автозагрузка

```
python tools/build_ota_launcher.py --channel dev --upload
python tools/build_ota_launcher.py --channel release --changelog "…" --upload
```

Скрипт соберёт APK, положит локально `tbox_launcher-v.…-{ru,en}.apk` + `version.json` и зальёт их в `/launcher-dev` или `/launcher-release` на Диске.
## Что уже работает

- HOME-активность, 3D-модель, ADAS/body/tire overlays
- Bottom bar (климат / сиденья через mbCAN)
- Freeform / embedded launch приложений
- Настройки авто + скрытая симуляция
- Media mini-player (через NotificationListener)
- TBox UDP через `TboxLinkManager` (MDC сеть + CRT CAN → скорость/руль/давление для 3D)
- OTA: проверка / скачивание / установка из экрана «Настроить»

## Что урезано / stub

- Нет trips / fuel / theme packs / floating dashboards Monitor
- `BackgroundService` — launch + mbCAN + тонкий TBox-линк
- `SettingsManager` — nav-hints, CAN mode, custom app icons, OTA channel

## Дальше

1. Убрать дубли кода (`ui/launcher`, `mbcan`) → shared library module
2. Вычистить неиспользуемые ресурсы, скопированные из `:app`
