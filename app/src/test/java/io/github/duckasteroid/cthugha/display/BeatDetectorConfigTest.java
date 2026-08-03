package io.github.duckasteroid.cthugha.display;

import com.asteroid.duck.opengl.util.audio.analysis.FrequencyBand;
import io.github.duckasteroid.cthugha.config.Config;
import org.ini4j.Ini;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BeatDetectorConfigTest {

    private static Config configFrom(String ini) throws Exception {
        Ini parsed = new Ini();
        parsed.load(new StringReader(ini));
        return new Config(parsed);
    }

    @Test
    void loadReturnsDefaultsWhenSectionAbsent() throws Exception {
        assertEquals(FrequencyBand.defaults(), BeatDetectorConfig.load(configFrom("")));
    }

    @Test
    void loadReturnsDefaultsWhenSectionEmpty() throws Exception {
        assertEquals(FrequencyBand.defaults(), BeatDetectorConfig.load(configFrom("[beats]\n")));
    }

    @Test
    void loadParsesConfiguredBands() throws Exception {
        Config cfg = configFrom("[beats]\nsub = 20-80\nkick = 80-200\n");
        assertEquals(List.of(new FrequencyBand("sub", 20f, 80f), new FrequencyBand("kick", 80f, 200f)),
                BeatDetectorConfig.load(cfg));
    }

    @Test
    void loadSkipsMalformedEntriesAndKeepsValidOnes() throws Exception {
        Config cfg = configFrom("[beats]\ngood = 100-200\nbad = not-a-range\n");
        assertEquals(List.of(new FrequencyBand("good", 100f, 200f)), BeatDetectorConfig.load(cfg));
    }

    @Test
    void loadFallsBackToDefaultsWhenNoEntryParses() throws Exception {
        Config cfg = configFrom("[beats]\nbad = not-a-range\n");
        assertEquals(FrequencyBand.defaults(), BeatDetectorConfig.load(cfg));
    }

    @Test
    void loadTuningReturnsDefaultsWhenSectionAbsent() throws Exception {
        assertEquals(BeatDetectorConfig.Tuning.defaults(), BeatDetectorConfig.loadTuning(configFrom("")));
    }

    @Test
    void loadTuningParsesConfiguredOverrides() throws Exception {
        Config cfg = configFrom("[BeatDetector]\nthreshold = 1.5\nsensitivity = 3.0\ndecay = 0.05\nhistory = 30\n");
        assertEquals(new BeatDetectorConfig.Tuning(30, 1.5f, 3.0f, 0.05f), BeatDetectorConfig.loadTuning(cfg));
    }

    @Test
    void loadTuningFallsBackPerKeyWhenPartiallyConfigured() throws Exception {
        Config cfg = configFrom("[BeatDetector]\nthreshold = 2.0\n");
        BeatDetectorConfig.Tuning defaults = BeatDetectorConfig.Tuning.defaults();
        BeatDetectorConfig.Tuning tuning = BeatDetectorConfig.loadTuning(cfg);
        assertEquals(2.0f, tuning.threshold());
        assertEquals(defaults.sensitivity(), tuning.sensitivity());
        assertEquals(defaults.decayPerFrame(), tuning.decayPerFrame());
        assertEquals(defaults.historyLength(), tuning.historyLength());
    }

    @Test
    void describeFormatsBandsCompactly() {
        List<FrequencyBand> bands = List.of(new FrequencyBand("bass", 20f, 250f), new FrequencyBand("snare", 250f, 2000f));
        assertEquals("bass 20-250Hz, snare 250-2000Hz", BeatDetectorConfig.describe(bands));
    }
}
