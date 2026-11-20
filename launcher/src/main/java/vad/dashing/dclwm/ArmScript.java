package vad.dashing.dclwm;

/** The one-shot script mb_daemon runs. It starts the helper and returns. */
public final class ArmScript {
    private ArmScript() {
    }

    public static String text(String dexPath) {
        String qdex = "'" + dexPath.replace("'", "'\\''") + "'";
        return "#!/system/bin/sh\n"
                + "DEX=" + qdex + "\n"
                + "PID=" + Protocol.PID_FILE + "\n"
                + "LOG=" + Protocol.LOG_FILE + "\n"
                + "MARK=" + Protocol.ARM_MARKER + "\n"
                + "USB_DEX='/data/data/ras.dashing.tbox.launcher/files/unlockusb.dex'\n"
                + "GATE_MARK='/data/local/tmp/usbmount_gate.done'\n"
                + "if [ -f \"$USB_DEX\" ] && [ ! -s \"$GATE_MARK\" ]; then\n"
                + "  CLASSPATH=\"$USB_DEX\" app_process /system/bin UnlockUsb > /data/local/tmp/unlockusb_boot.txt 2>&1\n"
                + "  chmod 666 /data/local/tmp/unlockusb_boot.txt\n"
                + "  echo OK > \"$GATE_MARK\"\n"
                + "  chmod 666 \"$GATE_MARK\"\n"
                + "fi\n"
                + "if [ -f \"$PID\" ]; then\n"
                + "  old=`cat \"$PID\"`\n"
                + "  if kill -0 \"$old\" 2>/dev/null; then\n"
                + "    echo already > \"$MARK\"\n"
                + "    chmod 666 \"$MARK\"\n"
                + "    rm -f \"$0\" " + Protocol.AUTORUN_FLAG + "\n"
                + "    setprop " + Protocol.AUTORUN_PROP + " ''\n"
                + "    exit 0\n"
                + "  fi\n"
                + "fi\n"
                + "if [ ! -f \"$DEX\" ]; then\n"
                + "  echo missing > \"$MARK\"\n"
                + "  chmod 666 \"$MARK\"\n"
                + "  rm -f \"$0\" " + Protocol.AUTORUN_FLAG + "\n"
                + "  setprop " + Protocol.AUTORUN_PROP + " ''\n"
                + "  exit 1\n"
                + "fi\n"
                + ": >> \"$LOG\"\n"
                + "chmod 666 \"$LOG\"\n"
                + "export CLASSPATH=\"$DEX\"\n"
                + "setsid /system/bin/app_process /system/bin vad.dashing.dclwm.Helper >> \"$LOG\" 2>&1 < /dev/null &\n"
                + "i=0\n"
                + "while [ \"$i\" -lt 25 ]; do\n"
                + "  if [ -f \"$PID\" ]; then\n"
                + "    echo started > \"$MARK\"\n"
                + "    chmod 666 \"$MARK\" \"$LOG\"\n"
                + "    rm -f \"$0\" " + Protocol.AUTORUN_FLAG + "\n"
                + "    setprop " + Protocol.AUTORUN_PROP + " ''\n"
                + "    exit 0\n"
                + "  fi\n"
                + "  i=$((i + 1))\n"
                + "  sleep 0.2\n"
                + "done\n"
                + "echo failed > \"$MARK\"\n"
                + "chmod 666 \"$MARK\"\n"
                + "rm -f \"$0\" " + Protocol.AUTORUN_FLAG + "\n"
                + "setprop " + Protocol.AUTORUN_PROP + " ''\n"
                + "exit 1\n";
    }

    public static void main(String[] args) {
        String path = args.length == 0 ? "/data/local/tmp/dclwm.dex" : args[0];
        System.out.print(text(path));
    }
}
