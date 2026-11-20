package vad.dashing.tbox.usbmount;

/**
 * Invokes hidden system calls as root (uid=0 via app_process) to clear vold's "secure keyguard" state.
 * When System UI is hidden (pm hide com.android.systemui), WTKeyguard never calls setLockScreenShown(false),
 * causing vold to refuse scanning and mounting NTFS/exFAT USB drives.
 */
public class UnlockUsb {
    public static void main(String[] args) {
        unlock();
    }

    public static boolean unlock() {
        boolean ok = true;
        try {
            Class<?> am = Class.forName("android.app.ActivityManager");
            Object iam = am.getMethod("getService").invoke(null);
            iam.getClass()
                    .getMethod("setLockScreenShown", boolean.class, boolean.class, int.class)
                    .invoke(iam, Boolean.FALSE, Boolean.FALSE, Integer.valueOf(-1));
            System.out.println("setLockScreenShown ok");
        } catch (Throwable t) {
            System.out.println("setLockScreenShown FAIL " + t);
            t.printStackTrace(System.out);
            ok = false;
        }

        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Object binder = sm.getMethod("getService", String.class).invoke(null, "vold");
            Class<?> ibinder = Class.forName("android.os.IBinder");
            Class<?> stub = Class.forName("android.os.IVold$Stub");
            Object vold = stub.getMethod("asInterface", ibinder).invoke(null, binder);
            vold.getClass()
                    .getMethod("onSecureKeyguardStateChanged", boolean.class)
                    .invoke(vold, Boolean.FALSE);
            System.out.println("vold.onSecureKeyguardStateChanged(false) ok");
        } catch (Throwable t) {
            System.out.println("vold FAIL " + t);
            t.printStackTrace(System.out);
            ok = false;
        }
        return ok;
    }
}
