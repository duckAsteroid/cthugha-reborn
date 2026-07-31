package io.github.duckasteroid.cthugha;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Best-effort request for a temporary CPU scheduling priority boost from Linux's GameMode daemon
 * (feralinteractive/gamemode) via its session D-Bus API, for the lifetime of this process. Regular
 * users generally can't raise their own nice value directly (requires CAP_SYS_NICE/root), but
 * GameMode grants it through a privileged daemon without either. A no-op on non-Linux platforms,
 * and on any failure (daemon not installed, D-Bus unavailable, etc.) - this is a performance
 * nicety, never a requirement to run.
 */
final class GameModeIntegration {

    private static final Logger LOG = LoggerFactory.getLogger(GameModeIntegration.class);
    private static final String INTERFACE = "com.feralinteractive.GameMode";
    private static final String OBJECT_PATH = "/com/feralinteractive/GameMode";

    private GameModeIntegration() {}

    static void registerIfAvailable() {
        if (!isLinux()) {
            return;
        }
        long pid = ProcessHandle.current().pid();
        if (callGameMode("RegisterGame", pid)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> callGameMode("UnregisterGame", pid)));
        }
    }

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    }

    private static boolean callGameMode(String method, long pid) {
        try {
            Process process = new ProcessBuilder(
                    "gdbus", "call", "--session",
                    "--dest", INTERFACE,
                    "--object-path", OBJECT_PATH,
                    "--method", INTERFACE + "." + method,
                    Long.toString(pid))
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                LOG.debug("GameMode {} timed out", method);
                return false;
            }
            if (process.exitValue() != 0) {
                LOG.debug("GameMode {} failed (exit {})", method, process.exitValue());
                return false;
            }
            return true;
        } catch (IOException e) {
            LOG.debug("GameMode unavailable, skipping {}: {}", method, e.toString());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
