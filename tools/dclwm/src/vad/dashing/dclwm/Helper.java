package vad.dashing.dclwm;

import android.net.Credentials;
import android.net.LocalServerSocket;
import android.net.LocalSocket;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * Root-side window helper. mb_daemon starts it once; the launcher then sends
 * one command per connection on the abstract socket {@link Protocol#SOCKET}.
 */
public final class Helper {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int LINE_LIMIT = 180;
    private static final int SOCKET_TIMEOUT_MS = 2000;
    private static final long UID_REFRESH_INTERVAL_MS = 5000L;

    public static void main(String[] args) {
        ProcessAm am = new ProcessAm();
        Session session = new Session(am);
        LocalServerSocket server;
        try {
            server = new LocalServerSocket(Protocol.SOCKET);
        } catch (Exception error) {
            System.err.println("bind " + error.getMessage());
            System.exit(1);
            return;
        }
        writePid();
        System.err.println("listening uid=" + android.os.Process.myUid());
        while (true) {
            LocalSocket client = null;
            try {
                client = server.accept();
                client.setSoTimeout(SOCKET_TIMEOUT_MS);
                serve(client, session, am);
            } catch (Exception error) {
                System.err.println("client " + error.getMessage());
            } finally {
                if (client != null) {
                    try {
                        client.close();
                    } catch (Exception ignored) {
                        // The client already went away.
                    }
                }
            }
        }
    }

    private static void serve(LocalSocket client, Session session, Am am) throws Exception {
        int peer = peerUid(client);
        int launcherUid = launcherUid(am, peer);
        if (!Session.isAllowed(peer, launcherUid)) {
            System.err.println("rejected peer=" + peer + " launcherUid=" + launcherUid);
            OutputStream out = client.getOutputStream();
            out.write("ERR forbidden\n".getBytes(UTF8));
            out.flush();
            try {
                client.shutdownOutput();
            } catch (Exception ignored) {
                // The peer already has the bytes.
            }
            return;
        }
        String line = readLine(new BufferedInputStream(client.getInputStream()));
        System.err.println("peer=" + peer + " launcherUid=" + launcherUid + " cmd=" + line);
        byte[] response = session.handle(line, peer, launcherUid).getBytes(UTF8);
        OutputStream out = client.getOutputStream();
        out.write(response);
        out.flush();
        try {
            client.shutdownOutput();
        } catch (Exception ignored) {
            // The peer already has the bytes.
        }
    }

    private static int peerUid(LocalSocket client) {
        try {
            Credentials credentials = client.getPeerCredentials();
            if (credentials == null) {
                return -1;
            }
            return credentials.getUid();
        } catch (Exception error) {
            System.err.println("peer " + error.getMessage());
            return -1;
        }
    }

    private static volatile int cachedLauncherUid = -1;
    private static volatile long lastUidRefreshMs = 0L;

    private static int launcherUid(Am am, int peerUid) {
        if (peerUid == 0) {
            return cachedLauncherUid;
        }
        int current = cachedLauncherUid;
        if (current >= 0 && peerUid == current) {
            return current;
        }
        long now = System.currentTimeMillis();
        if (current >= 0 && (now - lastUidRefreshMs) < UID_REFRESH_INTERVAL_MS) {
            return current;
        }
        lastUidRefreshMs = now;
        try {
            String text = am.run("pm", "list", "packages", "-U", Protocol.LAUNCHER_PACKAGE);
            int uid = LauncherUid.parsePmOutput(text, Protocol.LAUNCHER_PACKAGE);
            if (uid >= 0) {
                cachedLauncherUid = uid;
                return uid;
            }
            return cachedLauncherUid;
        } catch (AmFailure failure) {
            System.err.println("uid " + failure.getMessage());
            return cachedLauncherUid;
        }
    }

    private static String readLine(InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        boolean sawNewline = false;
        while (buf.size() < LINE_LIMIT) {
            int b = in.read();
            if (b < 0) {
                sawNewline = true;
                break;
            }
            if (b == '\n') {
                sawNewline = true;
                break;
            }
            if (b != '\r') {
                buf.write(b);
            }
        }
        if (!sawNewline) {
            int drained = 0;
            while (drained < 4096) {
                int b = in.read();
                if (b < 0 || b == '\n') {
                    break;
                }
                drained++;
            }
        }
        return new String(buf.toByteArray(), UTF8);
    }

    private static void writePid() {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(Protocol.PID_FILE);
            out.write(Integer.toString(android.os.Process.myPid()).getBytes(UTF8));
            out.write('\n');
        } catch (Exception error) {
            System.err.println("pid " + error.getMessage());
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {
                    // The pid file is best-effort.
                }
            }
        }
    }
}
