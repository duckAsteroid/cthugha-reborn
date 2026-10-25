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
        Config cfg = configFrom("[BeatDetector]\nthreshold = 1.5\nsensitivity = 3.0\ndecay_time = 0.25\nhistory = 30\n");
        assertEquals(new BeatDetectorConfig.Tuning(30, 1.5f, 3.0f, 0.25f), BeatDetectorConfig.loadTuning(cfg));
    }

    @Test
    void loadTuningConvertsALegacyPerFrameDecayAt60Fps() throws Exception {
        // 0.156 per frame at 60 fps: full scale gone in 1 / (0.156 * 60) = 0.107 s.
        Config cfg = configFrom("[BeatDetector]\ndecay = 0.156\n");
        assertEquals(0.1068f, BeatDetectorConfig.loadTuning(cfg).decaySeconds(), 1e-4);
        // decay_time wins over a leftover per-frame value.
        cfg = configFrom("[BeatDetector]\ndecay = 0.156\ndecay_time = 2.0\n");
        assertEquals(2.0f, BeatDetectorConfig.loadTuning(cfg).decaySeconds());
    }

    @Test
    void perFrameDecayConversionClampsToTheRange() {
        assertEquals(1.0, BeatDetectorConfig.decaySecondsFromPerFrame(1.0 / 60), 1e-9);
        assertEquals(BeatDetectorConfig.MAX_DECAY_SECONDS, BeatDetectorConfig.decaySecondsFromPerFrame(0));
        assertEquals(BeatDetectorConfig.MAX_DECAY_SECONDS, BeatDetectorConfig.decaySecondsFromPerFrame(0.0001));
        assertEquals(BeatDetectorConfig.MIN_DECAY_SECONDS, BeatDetectorConfig.decaySecondsFromPerFrame(5));
    }

    @Test
    void beatsFadeInTheSameTimeAtAnyRefreshRate() {
        // A full-strength beat, then silence: after 0.25 s of updates at 24 Hz or 60 Hz the
        // published strength has fallen by the same amount (decay time 1 s -> 0.25 lost).
        assertEquals(strengthAfterQuarterSecond(24), strengthAfterQuarterSecond(60), 0.02);
    }

    private static float strengthAfterQuarterSecond(int hz) {
        int bins = 64;
        BeatDetectorConfig.Tuning tuning = new BeatDetectorConfig.Tuning(4, 1.3f, 2.0f, 1.0f);
        com.asteroid.duck.opengl.util.audio.analysis.BeatDetector detector =
                new com.asteroid.duck.opengl.util.audio.analysis.BeatDetector(
                        List.of(new FrequencyBand("all", 0f, 24_000f)), bins * 2, 48_000f,
                        tuning.historyLength(), tuning.threshold(), tuning.sensitivity(),
                        com.asteroid.duck.opengl.util.audio.analysis.BeatDetector.DEFAULT_NOISE_FLOOR,
                        tuning.decayTime());
        float[] quiet = new float[bins];
        java.util.Arrays.fill(quiet, 0.05f);
        float[] loud = new float[bins];
        java.util.Arrays.fill(loud, 1.0f);
        double dt = 1.0 / hz;
        for (int i = 0; i < 8; i++) detector.update(quiet, dt);
        detector.update(loud, dt);
        float peak = detector.getBeatStrength("all");
        for (int i = 0; i < hz / 4; i++) detector.update(new float[bins], dt);
        float after = detector.getBeatStrength("all");
        org.junit.jupiter.api.Assertions.assertTrue(peak > 0.9f, "beat should trigger, was " + peak);
        return after;
    }

    @Test
    void loadTuningFallsBackPerKeyWhenPartiallyConfigured() throws Exception {
        Config cfg = configFrom("[BeatDetector]\nthreshold = 2.0\n");
        BeatDetectorConfig.Tuning defaults = BeatDetectorConfig.Tuning.defaults();
        BeatDetectorConfig.Tuning tuning = BeatDetectorConfig.loadTuning(cfg);
        assertEquals(2.0f, tuning.threshold());
        assertEquals(defaults.sensitivity(), tuning.sensitivity());
        assertEquals(defaults.decaySeconds(), tuning.decaySeconds());
        assertEquals(defaults.historyLength(), tuning.historyLength());
    }

    @Test
    void describeFormatsBandsCompactly() {
        List<FrequencyBand> bands = List.of(new FrequencyBand("bass", 20f, 250f), new FrequencyBand("snare", 250f, 2000f));
        assertEquals("bass 20-250Hz, snare 250-2000Hz", BeatDetectorConfig.describe(bands));
    }
}
