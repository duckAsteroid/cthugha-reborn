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
        new CthughaWindow(stdinEnabled, remoteConfig, dumpConfig).displayLoop();
    }
}
