package io.github.duckasteroid.cthugha.dump;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DumpConfigTest {

    @Test
    void defaultsAreDisabled() {
        DumpConfig cfg = DumpConfig.parse(new String[0]);
        assertFalse(cfg.enabled);
    }

    @Test
    void dumpParamsFlagEnablesWithDefaultPath() {
        DumpConfig cfg = DumpConfig.parse(new String[]{"--dump-params"});
        assertTrue(cfg.enabled);
        assertEquals(Path.of("params-dump.json"), cfg.outputFile);
        assertInstanceOf(JsonDumpFormat.class, cfg.format);
    }

    @Test
    void dumpParamsEqualsFormOverridesPath() {
        DumpConfig cfg = DumpConfig.parse(new String[]{"--dump-params=output.txt"});
        assertTrue(cfg.enabled);
        assertEquals(Path.of("output.txt"), cfg.outputFile);
    }

    @Test
    void dumpFormatTextSelectsTextFormat() {
        DumpConfig cfg = DumpConfig.parse(new String[]{"--dump-params", "--dump-format=text"});
        assertInstanceOf(TextDumpFormat.class, cfg.format);
    }

    @Test
    void dumpFormatTxtAliasSelectsTextFormat() {
        DumpConfig cfg = DumpConfig.parse(new String[]{"--dump-params", "--dump-format=txt"});
        assertInstanceOf(TextDumpFormat.class, cfg.format);
    }

    @Test
    void dumpFormatJsonSelectsJsonFormat() {
        DumpConfig cfg = DumpConfig.parse(new String[]{"--dump-params", "--dump-format=json"});
        assertInstanceOf(JsonDumpFormat.class, cfg.format);
    }

    @Test
    void unknownFormatFallsBackToJson() {
        DumpConfig cfg = DumpConfig.parse(new String[]{"--dump-params", "--dump-format=xml"});
        assertInstanceOf(JsonDumpFormat.class, cfg.format);
    }
}
