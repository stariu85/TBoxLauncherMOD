package plus.monjaro;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class MonjaroNavReceiver extends BroadcastReceiver {
    private static final String TAG = "MonjaroNavReceiver";
    private static final String[] TARGET_LAUNCHER_PACKAGES = new String[] {
        "ras.dashing.tbox.launcher",
        "vad.dashing.tbox"
    };
    private static final Set<String> ALLOWED_ACTIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "com.yandex.LANES",
        "com.yandex.LANES_BITMAP",
        "com.yandex.LANES_BITMAP_CLEAR",
        "com.yandex.LANE_DIST",
        "com.yandex.LANE_SIGN",
        "com.yandex.MINIMAP",
        "plus.monjaro.NAVIGATION_ENDED",
        "plus.monjaro.TRAFFIC_LIGHT_UPDATE"
    )));

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (action == null || !ALLOWED_ACTIONS.contains(action)) {
            Log.w(TAG, "Ignoring unexpected action: " + action);
            return;
        }

        Bundle extras = null;
        try {
            extras = intent.getExtras();
        } catch (Throwable t) {
            Log.e(TAG, "Failed to read extras for action " + action, t);
            return;
        }

        for (String targetPkg : TARGET_LAUNCHER_PACKAGES) {
            try {
                Intent forward = new Intent(action);
                forward.setPackage(targetPkg);
                if (extras != null) {
                    forward.putExtras(extras);
                }
                forward.addFlags(Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                context.sendBroadcast(forward);
                Log.d(TAG, "Forwarded to " + targetPkg + ": " + action);
            } catch (Throwable t) {
                Log.e(TAG, "Failed to forward broadcast to " + targetPkg, t);
            }
        }
    }
}
