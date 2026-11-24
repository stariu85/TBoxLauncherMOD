package plus.monjaro;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class MonjaroNavReceiver extends BroadcastReceiver {
    private static final String TAG = "MonjaroNavReceiver";
    private static final String[] TARGET_LAUNCHER_PACKAGES = new String[] {
        "ras.dashing.tbox.launcher",
        "vad.dashing.tbox"
    };

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        String action = intent.getAction();
        Bundle extras = intent.getExtras();

        for (String targetPkg : TARGET_LAUNCHER_PACKAGES) {
            Intent forward = new Intent(action);
            forward.setPackage(targetPkg);
            if (extras != null) {
                forward.putExtras(extras);
            }
            forward.addFlags(Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_INCLUDE_STOPPED_PACKAGES);

            try {
                context.sendBroadcast(forward);
                Log.d(TAG, "Forwarded to " + targetPkg + ": " + action);
            } catch (Throwable t) {
                Log.e(TAG, "Failed to forward broadcast to " + targetPkg, t);
            }
        }
    }
}
