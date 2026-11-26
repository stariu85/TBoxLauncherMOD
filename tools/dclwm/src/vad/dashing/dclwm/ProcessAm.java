package vad.dashing.dclwm;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

/** ProcessBuilder wrapper. The argument list is executed as-is, with no shell. */
public final class ProcessAm implements Am {
    private static final long TIMEOUT_MS = 8000L;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    @Override
    public String run(String... args) throws AmFailure {
        Process process;
        try {
            process = new ProcessBuilder(args).redirectErrorStream(true).start();
        } catch (Exception error) {
            throw new AmFailure(error.getMessage());
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        Thread reader = new Thread(new Drain(process.getInputStream(), buf), "dclwm-am");
        reader.setDaemon(true);
        reader.start();
        int code;
        try {
            if (!process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                reader.join(500);
                throw new AmFailure("timeout");
            }
            code = process.exitValue();
            reader.join(500);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new AmFailure("interrupted");
        } finally {
            closeQuietly(process.getInputStream());
            closeQuietly(process.getErrorStream());
            closeQuietly(process.getOutputStream());
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
        String text;
        synchronized (buf) {
            text = new String(buf.toByteArray(), UTF8);
        }
        if (code != 0) {
            throw new AmFailure(text);
        }
        return text;
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // Best-effort stream cleanup.
            }
        }
    }

    private static final class Drain implements Runnable {
        private final InputStream in;
        private final ByteArrayOutputStream out;

        Drain(InputStream in, ByteArrayOutputStream out) {
            this.in = in;
            this.out = out;
        }

        @Override
        public void run() {
            byte[] buf = new byte[1024];
            try {
                int n;
                while ((n = in.read(buf)) >= 0) {
                    synchronized (out) {
                        out.write(buf, 0, n);
                    }
                }
            } catch (Exception ignored) {
                // The process was destroyed or the pipe closed.
            }
        }
    }
}
