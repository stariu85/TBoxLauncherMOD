/**
 * Default-package entrypoint for `app_process /system/bin UnlockUsb`,
 * delegating directly to [vad.dashing.tbox.usbmount.UnlockUsb].
 */
public class UnlockUsb {
    public static void main(String[] args) {
        vad.dashing.tbox.usbmount.UnlockUsb.main(args);
    }
}
