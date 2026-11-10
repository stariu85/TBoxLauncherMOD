package vad.dashing.tbox

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import vad.dashing.tbox.ui.launcher.YandexNavRepository

/**
 * Required for MediaSession / media controls binding on the home player
 * and Yandex Navigator notification state updates.
 */
class MediaControlNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "MediaControlNotificationListenerService CONNECTED")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn != null) {
            Log.d(TAG, "onNotificationPosted: pkg=${sbn.packageName}")
            YandexNavRepository.onNotificationPosted(this, sbn)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn != null) {
            Log.d(TAG, "onNotificationRemoved: pkg=${sbn.packageName}")
            YandexNavRepository.onNotificationRemoved(sbn)
        }
    }

    private companion object {
        const val TAG = "LauncherMediaNls"
    }
}
