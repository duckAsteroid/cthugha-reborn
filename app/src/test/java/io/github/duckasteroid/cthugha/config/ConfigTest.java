package io.github.duckasteroid.cthugha.config;

import org.ini4j.Ini;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    @Test
    void setConfigUpdatesValueVisibleThroughGetConfig() {
        Config cfg = new Config(new Ini());
        cfg.setConfig("audio", "preferred_device", "Built-in Audio:Port 1");
        assertEquals("Built-in Audio:Port 1", cfg.getConfig("audio", "preferred_device", ""));
    }

    @Test
    void setConfigWithoutBackingFileDoesNotThrow() {
        // new Ini() has no associated file, so store() has nothing to write to; setConfig
        // should log and swallow that rather than propagate.
        Config cfg = new Config(new Ini());
        assertDoesNotThrow(() -> cfg.setConfig("audio", "preferred_device", "Some Device"));
    }

    @Test
    void setConfigPersistsToBackingFile(@TempDir Path tempDir) throws IOException {
        File file = tempDir.resolve("cthugha-test.ini").toFile();
        Ini ini = new Ini();
        ini.setFile(file);

        Config cfg = new Config(ini);
        cfg.setConfig("audio", "preferred_device", "USB Mic:Port 1");

        Ini reloaded = new Ini(file);
        assertEquals("USB Mic:Port 1", reloaded.get("audio", "preferred_device"));
    }
}
