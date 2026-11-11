# Руководство по интеграции с DCL Launcher API v1.3 (для разработчика TBox Monitor)

Данная инструкция описывает актуальный протокол подключения TBox Monitor (`vad.dashing.tbox`) к DCL Launcher (`ras.dashing.tbox.launcher`) для получения реального состояния экрана, геометрии баров/ADAS-панели, управления приложениями и работы с `localaccess`.

---

## 1. Основные сведения о протоколе

| Параметр | Значение |
| :--- | :--- |
| **Authority** | `ras.dashing.tbox.launcher.api` |
| **URI** | `content://ras.dashing.tbox.launcher.api` |
| **Авторизация** | Автоматическая по UID вызывающего пакета (`vad.dashing.tbox`). |
| **Прямой адрес Push** | Broadcast-события отправляются с явным пакетом `vad.dashing.tbox`. |
| **Гарантия согласованности** | Ответ метода `getUiState` строго равен последнему переданному Push-событию (один и тот же номер `revision`). |

---

## 2. Поля снимка состояния UI (`DclUiState`)

Каждый Push-broadcast и вызов `getUiState` содержит полный согласованный снимок интерфейса:

| Поле в Bundle | Тип | Описание |
| :--- | :--- | :--- |
| `status` | String | `"ok"` при успешном вызове. |
| `screenMode` | String | `"home"` \| `"single"` \| `"full_width"` \| `"fullscreen"` \| `"split"` |
| `foregroundPackage` | String | Имя пакета текущего активного стороннего приложения (или `""`). |
| `adasPanelVisible` | Boolean | Реальная видимость панели ADAS на экране (`false` при fullscreen или открытых настройках). |
| `topBarVisible` | Boolean | Реальная видимость верхнего бара на экране (`false` при fullscreen или открытых настройках). |
| `bottomBarVisible` | Boolean | Реальная видимость нижнего бара (`true` на рабочем столе, в `app_area` и в окне настроек; `false` в fullscreen). |
| `overlayOpen` | Boolean | `true`, если поверх интерфейса/приложений открыты настройки DCL, Vehicle Settings или шторка Control Center. |
| `immersive` | Boolean | `true` при работе приложения на полный экран. |
| `topBarHeightPx` | Int | Высота верхнего бара в px (`0`, если скрыт). |
| `bottomBarHeightPx` | Int | Высота нижнего бара в px (`0`, если скрыт). |
| `adasPanelBounds` | String | Границы ADAS-зоны `"left,top,right,bottom"` (`"0,0,0,0"`, если скрыта). |
| `appAreaBounds` | String | Границы рабочей зоны приложений `"left,top,right,bottom"` (`"0,0,displayW,displayH"`, если fullscreen). |
| `displayWidthPx` | Int | Ширина экрана в пикселях (например, `1920`). |
| `displayHeightPx` | Int | Высота экрана в пикселях (например, `1080`). |
| `notificationShadeExpanded` | Boolean | `true`, если открыта верхняя шторка управления DCL. |
| `revision` | Long | Монотонно растущий счетчик ревизий снимка экрана. |
| `updatedAtEpochMs` | Long | Метка времени формирования снимка (Unix epoch ms). |

---

## 3. Поведение лаунчера в различных режимах

### 1. Рабочий стол (Home)
- `screenMode` = `"home"`, `foregroundPackage` = `""`
- `adasPanelVisible` = `true` (согласно настройкам пользователя)
- `topBarVisible` = `true`, `bottomBarVisible` = `true`, `overlayOpen` = `false`

### 2. Приложение в рабочей зоне (`app_area`)
- `screenMode` = `"single"`, `foregroundPackage` = `"pkg.name"`
- `adasPanelVisible` = `true`, `topBarVisible` = `true`, `bottomBarVisible` = `true`
- `appAreaBounds` = `"344,51,1920,998"` (справа от ADAS, между верхним и нижним барами)

### 3. Экран настроек DCL / Vehicle Settings
- `overlayOpen` = `true`
- `adasPanelVisible` = `false` (перекрыта окном настроек)
- `topBarVisible` = `false` (перекрыт окном настроек)
- `bottomBarVisible` = `true` (нижняя панель отображается внизу экрана настроек)
- **При закрытии настроек**: шлется мгновенный push с полным восстановлением значений баров и `overlayOpen = false`.

### 4. Полноэкранный режим (Fullscreen)
- `screenMode` = `"fullscreen"`, `foregroundPackage` = `"pkg.name"`, `immersive` = `true`
- `adasPanelVisible` = `false`, `topBarVisible` = `false`, `bottomBarVisible` = `false`
- `adasPanelBounds` = `"0,0,0,0"`, `appAreaBounds` = `"0,0,1920,1080"`

### 5. Режим на всю ширину между двумя барами без ADAS (`full_width`)
- `screenMode` = `"full_width"`, `foregroundPackage` = `"pkg.name"`, `immersive` = `false`
- `adasPanelVisible` = `false`, `topBarVisible` = `true`, `bottomBarVisible` = `true`
- `adasPanelBounds` = `"0,0,0,0"`, `appAreaBounds` = `"0,51,1920,998"` (на всю ширину экрана между верхним и нижним барами)

---

## 4. Подписка на Push-события Broadcast

DCL отправляет Push-уведомления при **любом** изменении видимости баров, ADAS, оверлея настроек или режима экрана.

### `AndroidManifest.xml` (TBox Monitor)

```xml
<receiver
    android:name=".receiver.DclUiStateReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="ras.dashing.tbox.launcher.action.UI_STATE_CHANGED" />
        <action android:name="ras.dashing.tbox.launcher.action.UI_CHROME_CHANGED" />
    </intent-filter>
</receiver>
```

### `DclUiStateReceiver.kt` (TBox Monitor)

```kotlin
package vad.dashing.tbox.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DclUiStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == "ras.dashing.tbox.launcher.action.UI_STATE_CHANGED" ||
            action == "ras.dashing.tbox.launcher.action.UI_CHROME_CHANGED"
        ) {
            val extras = intent.extras ?: return
            
            val screenMode = extras.getString("screenMode", "home")
            val foregroundPackage = extras.getString("foregroundPackage", "")
            val adasPanelVisible = extras.getBoolean("adasPanelVisible", true)
            val topBarVisible = extras.getBoolean("topBarVisible", true)
            val bottomBarVisible = extras.getBoolean("bottomBarVisible", true)
            val overlayOpen = extras.getBoolean("overlayOpen", false)
            val adasBoundsStr = extras.getString("adasPanelBounds", "0,51,344,998")
            val appAreaBoundsStr = extras.getString("appAreaBounds", "344,51,1920,998")
            val revision = extras.getLong("revision", 1L)

            // Перестроить плавающие окна и логику автоматизаций TBox Monitor
            onUiStateUpdated(
                screenMode = screenMode,
                foregroundPackage = foregroundPackage,
                adasPanelVisible = adasPanelVisible,
                topBarVisible = topBarVisible,
                bottomBarVisible = bottomBarVisible,
                overlayOpen = overlayOpen,
                adasBounds = parseRect(adasBoundsStr),
                appAreaBounds = parseRect(appAreaBoundsStr),
                revision = revision
            )
        }
    }

    private fun parseRect(str: String): android.graphics.Rect {
        val p = str.split(",")
        if (p.size == 4) {
            return android.graphics.Rect(
                p[0].toIntOrNull() ?: 0,
                p[1].toIntOrNull() ?: 0,
                p[2].toIntOrNull() ?: 0,
                p[3].toIntOrNull() ?: 0
            )
        }
        return android.graphics.Rect(0, 0, 0, 0)
    }

    private fun onUiStateUpdated(
        screenMode: String,
        foregroundPackage: String,
        adasPanelVisible: Boolean,
        topBarVisible: Boolean,
        bottomBarVisible: Boolean,
        overlayOpen: Boolean,
        adasBounds: android.graphics.Rect,
        appAreaBounds: android.graphics.Rect,
        revision: Long
    ) {
        // Логика подстройки оверлеев TBox Monitor
    }
}
```

---

## 5. Запрос текущего состояния (`getUiState`)

Если TBox Monitor запускается перезапускается или производит инициализацию, он может запросить актуальный снимок экрана напрямую:

```kotlin
import android.content.Context
import android.net.Uri
import android.os.Bundle

object DclLauncherApi {
    private val LAUNCHER_API_URI = Uri.parse("content://ras.dashing.tbox.launcher.api")

    fun getUiState(context: Context): Bundle? {
        return try {
            context.contentResolver.call(LAUNCHER_API_URI, "getUiState", null, null)
        } catch (e: Exception) {
            null
        }
    }
}
```

---

## 6. Запуск приложений (`launchApp`)

Для открытия сторонних приложений или самого TBox Monitor используйте метод `launchApp`.

```kotlin
fun launchApp(
    context: Context,
    packageName: String,
    mode: String = "app_area", // "app_area", "full_width" (без ADAS) или "fullscreen"
    activityClass: String? = null
): Boolean {
    val extras = Bundle().apply {
        putString("packageName", packageName)
        putString("mode", mode)
        if (!activityClass.isNullOrEmpty()) {
            putString("activityClass", activityClass)
        }
        putString("requestId", java.util.UUID.randomUUID().toString())
    }

    return try {
        val response = context.contentResolver.call(
            Uri.parse("content://ras.dashing.tbox.launcher.api"),
            "launchApp",
            null,
            extras
        )
        response?.getString("status") == "ok"
    } catch (e: Exception) {
        false
    }
}
```

---

## 7. Критично: Работа с ContentProvider `localaccess`

Для предотвращения ситуации, когда Android ActivityManager (AMS) при обновлении APK TBox Monitor убивает DCL Launcher (`ras.dashing.tbox.launcher`) из-за удерживаемой стабильной связи с ContentProvider:

### Правило обращения к `vad.dashing.tbox.localaccess`:
1. **Строго использовать `acquireUnstableContentProviderClient`**
2. **Делать одиночный вызов и НЕМЕДЛЕННО закрывать клиент через `close()`**
3. **НЕ удерживать persistent connection, курсоры или ContentObserver**

### Пример корректной реализации вызова в TBox Monitor:

```kotlin
import android.content.Context
import android.net.Uri
import android.os.Bundle

object LocalAccessCaller {
    private val AUTHORITY_URI = Uri.parse("content://vad.dashing.tbox.localaccess")

    fun getAccess(context: Context, interactive: Boolean = false): Bundle? {
        val extras = Bundle().apply {
            putBoolean("interactive", interactive)
        }

        // КРИТИЧНО: Использовать unstable клиент
        val client = context.contentResolver.acquireUnstableContentProviderClient(AUTHORITY_URI) 
            ?: return null

        return try {
            client.call("getAccess", null, extras)
        } catch (e: Exception) {
            null
        } finally {
            // КРИТИЧНО: Сразу закрывать клиент после каждого вызова
            client.close()
        }
    }
}
```

*Примечание: HTTP API Token TBox Monitor сохраняется лаунчером и автоматически сохраняется между обновлениями приложений.*
