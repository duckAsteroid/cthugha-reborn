package io.github.duckasteroid.cthugha;

import io.github.duckasteroid.cthugha.display.CthughaWindow;
import io.github.duckasteroid.cthugha.dump.DumpConfig;
import io.github.duckasteroid.cthugha.remote.RemoteConfig;

import java.util.Arrays;

public class Main {

    public static void main(String[] args) throws Exception {
        boolean stdinEnabled = Arrays.asList(args).contains("--stdin");
        RemoteConfig remoteConfig = RemoteConfig.parse(args);
        DumpConfig dumpConfig = DumpConfig.parse(args);
        CthughaWindow window = new CthughaWindow(stdinEnabled, remoteConfig, dumpConfig);
        // Backstop for termination paths that skip CthughaWindow#dispose (SIGTERM/Ctrl+C, an
        // uncaught exception escaping displayLoop, etc.) so the persisted "current" state (see
        // issue #3) reflects the last few seconds of the session even then, not just on a clean
        // window-close exit.
        Runtime.getRuntime().addShutdownHook(new Thread(
                window::flushCurrentStateOnShutdown, "current-state-shutdown-flush"));
        window.displayLoop();
    }
}
