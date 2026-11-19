package vad.dashing.dclwm;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Starts the helper from the launcher process. Call {@link #ensure(Context)}
 * from a background thread at process start and on BOOT_COMPLETED.
 * The dex must be in assets under {@link Protocol#DEX_ASSET}.
 */
public final class Starter {
    private static final int WAIT_MS = 8000;

    private Starter() {
    }

    public static boolean isRunning() {
        try {
            new Client().ping();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isStorageMounted() {
        return android.os.Environment.MEDIA_MOUNTED.equals(
                android.os.Environment.getExternalStorageState());
    }

    public static void ensure(Context context) throws Exception {
        vad.dashing.tbox.DclLogRecorder.tryFlushMemoryBuffer(context);

        if (isRunning()) {
            android.util.Log.i("dclwm", "dclwm is already running, skipping Starter.ensure");
            vad.dashing.tbox.DclLogRecorder.log("DCLWM", "dclwm is already running, skipping Starter.ensure");
            return;
        }

        // Guard: Wait for external storage (sdcard) to be mounted before attempting autorun
        String storageState = android.os.Environment.getExternalStorageState();
        if (!android.os.Environment.MEDIA_MOUNTED.equals(storageState)) {
            android.util.Log.w("dclwm", "Storage not mounted yet (state=" + storageState + "), skipping Starter.ensure");
            vad.dashing.tbox.DclLogRecorder.log("DCLWM", "Storage not mounted yet (state=" + storageState + "), skipping Starter.ensure");
            return;
        }

        synchronized (vad.dashing.tbox.usbmount.RootOrchestrator.ROOT_LOCK) {
            vad.dashing.tbox.DclLogRecorder.log("DCLWM", "Starting dclwm: copying dex and writing autorun.sh");
            File dex = new File(context.getFilesDir(), Protocol.DEX_ASSET);
            copyAsset(context, dex);
            String script = ArmScript.text(dex.getAbsolutePath());
            File autorun = new File(Protocol.AUTORUN);
            writeLf(autorun, script);
            exec("chmod", "777", autorun.getAbsolutePath());
            new File(Protocol.AUTORUN_FLAG).createNewFile();
            exec("chmod", "666", Protocol.AUTORUN_FLAG);
            exec("setprop", Protocol.AUTORUN_PROP, "");
            Thread.sleep(300);
            exec("setprop", Protocol.AUTORUN_PROP, Protocol.AUTORUN_FLAG);
            long deadline = System.currentTimeMillis() + WAIT_MS;
            File marker = new File(Protocol.ARM_MARKER);
            while (System.currentTimeMillis() < deadline) {
                String text = readIfAny(marker);
                if (text.contains("started") || text.contains("already")) {
                    vad.dashing.tbox.DclLogRecorder.log("DCLWM", "dclwm started successfully: marker=" + text);
                    return;
                }
                if (text.contains("missing") || text.contains("failed")) {
                    vad.dashing.tbox.DclLogRecorder.log("DCLWM", "dclwm failed to stay up: marker=" + text);
                    throw new IllegalStateException("dclwm did not stay up, see " + Protocol.LOG_FILE);
                }
                Thread.sleep(200);
            }
            vad.dashing.tbox.DclLogRecorder.log("DCLWM", "mb_daemon did not start dclwm within deadline");
            throw new IllegalStateException("mb_daemon did not start dclwm");
        }
    }

    private static void copyAsset(Context context, File dest) throws Exception {
        InputStream in = context.getAssets().open(Protocol.DEX_ASSET);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(dest);
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
        } finally {
            in.close();
            if (out != null) {
                out.close();
            }
        }
    }

    private static void writeLf(File file, String text) throws Exception {
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(text.replace("\r\n", "\n").replace('\r', '\n').getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    private static String readIfAny(File file) {
        try {
            if (!file.canRead()) {
                return "";
            }
            java.io.FileInputStream in = new java.io.FileInputStream(file);
            try {
                byte[] buf = new byte[(int) Math.min(file.length(), 200)];
                int n = in.read(buf);
                if (n <= 0) {
                    return "";
                }
                return new String(buf, 0, n, "UTF-8");
            } finally {
                in.close();
            }
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void exec(String... args) throws Exception {
        Process process = new ProcessBuilder(args).redirectErrorStream(true).start();
        process.waitFor();
    }
}
