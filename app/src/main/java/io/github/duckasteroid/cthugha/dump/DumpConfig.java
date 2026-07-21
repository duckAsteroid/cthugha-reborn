package io.github.duckasteroid.cthugha.dump;

import java.nio.file.Path;

public class DumpConfig {

    public boolean enabled = false;
    public Path outputFile = Path.of("params-dump.json");
    public DumpFormat format = new JsonDumpFormat();

    public static DumpConfig parse(String[] args) {
        DumpConfig config = new DumpConfig();
        for (String arg : args) {
            if (arg.equals("--dump-params")) {
                config.enabled = true;
            } else if (arg.startsWith("--dump-params=")) {
                config.enabled = true;
                config.outputFile = Path.of(arg.substring("--dump-params=".length()));
            } else if (arg.startsWith("--dump-format=")) {
                String fmt = arg.substring("--dump-format=".length()).toLowerCase();
                config.format = switch (fmt) {
                    case "text", "txt" -> new TextDumpFormat();
                    default -> new JsonDumpFormat();
                };
            }
        }
        return config;
    }
}
