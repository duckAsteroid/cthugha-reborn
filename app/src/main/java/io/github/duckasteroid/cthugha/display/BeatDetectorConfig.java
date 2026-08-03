package io.github.duckasteroid.cthugha.display;

import com.asteroid.duck.opengl.util.audio.analysis.FrequencyBand;
import io.github.duckasteroid.cthugha.config.Config;
import org.ini4j.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Loads {@link com.asteroid.duck.opengl.util.audio.analysis.BeatDetector} configuration from
 * {@code cthugha.ini}: the monitored frequency bands ({@code [beats]}) and the detector's global
 * tuning knobs ({@code [BeatDetector]}). Falls back to the detector's own built-in defaults for
 * anything absent or empty.
 *
 * <h2>Format</h2>
 * <pre>
 * [beats]
 * bass  = 20-250
 * snare = 250-2000
 * hihat = 2000-20000
 *
 * [BeatDetector]
 * threshold   = 1.3
 * sensitivity = 2.0
 * decay       = 0.016667
 * history     = 43
 * </pre>
 *
 * <p>Each {@code [beats]} key is a band name (used by {@code bass()}/{@code beat(name)} in
 * binding scripts — renaming a band here means any script referencing the old name silently
 * returns 0); the value is {@code fMin-fMax} in Hz. Malformed entries are skipped with a warning
 * rather than failing the whole section.</p>
 */
public final class BeatDetectorConfig {

    private static final Logger LOG = LoggerFactory.getLogger(BeatDetectorConfig.class);
    private static final String BANDS_SECTION = "beats";
    private static final String TUNING_SECTION = "BeatDetector";

    private BeatDetectorConfig() {}

    /** Global, non-band tuning knobs for {@link com.asteroid.duck.opengl.util.audio.analysis.BeatDetector}. */
    public record Tuning(int historyLength, float threshold, float sensitivity, float decayPerFrame) {
        /** Matches {@code BeatDetector}'s own convenience-constructor defaults. */
        public static Tuning defaults() {
            return new Tuning(43, 1.3f, 2.0f, 1f / 60f);
        }
    }

    /** Reads the configured bands from {@code cthugha.ini}, or the built-in defaults if unset. */
    public static List<FrequencyBand> load() {
        return load(Config.singleton());
    }

    /** As {@link #load()}, reading from an explicit {@link Config} instance (e.g. for testing). */
    public static List<FrequencyBand> load(Config config) {
        Optional<Profile.Section> section = config.getSection(BANDS_SECTION);
        // Empty (e.g. every entry commented out) is indistinguishable from absent — both just
        // mean "use the defaults", not a misconfiguration worth warning about.
        if (section.isEmpty() || section.get().isEmpty()) {
            return FrequencyBand.defaults();
        }

        List<FrequencyBand> bands = new ArrayList<>();
        for (String name : section.get().keySet()) {
            String range = section.get().get(name);
            try {
                bands.add(parseBand(name, range));
            } catch (RuntimeException e) {
                LOG.warn("[beats] '{}' = '{}': {}", name, range, e.getMessage());
            }
        }

        if (bands.isEmpty()) {
            LOG.warn("[beats] section has entries but none parsed as valid bands; using defaults");
            return FrequencyBand.defaults();
        }
        return bands;
    }

    /** Reads the detector's global tuning knobs from {@code [BeatDetector]}, or built-in defaults if unset. */
    public static Tuning loadTuning() {
        return loadTuning(Config.singleton());
    }

    /** As {@link #loadTuning()}, reading from an explicit {@link Config} instance (e.g. for testing). */
    public static Tuning loadTuning(Config cfg) {
        Tuning defaults = Tuning.defaults();
        int historyLength = cfg.getConfigAs(TUNING_SECTION, "history",
                String.valueOf(defaults.historyLength()), Integer::parseInt);
        float threshold = cfg.getConfigAs(TUNING_SECTION, "threshold",
                String.valueOf(defaults.threshold()), Float::parseFloat);
        float sensitivity = cfg.getConfigAs(TUNING_SECTION, "sensitivity",
                String.valueOf(defaults.sensitivity()), Float::parseFloat);
        float decayPerFrame = cfg.getConfigAs(TUNING_SECTION, "decay",
                String.valueOf(defaults.decayPerFrame()), Float::parseFloat);
        return new Tuning(historyLength, threshold, sensitivity, decayPerFrame);
    }

    private static FrequencyBand parseBand(String name, String range) {
        if (range == null) throw new IllegalArgumentException("missing Hz range");
        int dash = range.indexOf('-');
        if (dash <= 0 || dash == range.length() - 1) {
            throw new IllegalArgumentException("expected 'fMin-fMax', e.g. '250-2000'");
        }
        float fMin = Float.parseFloat(range.substring(0, dash).trim());
        float fMax = Float.parseFloat(range.substring(dash + 1).trim());
        if (fMin >= fMax) throw new IllegalArgumentException("fMin must be less than fMax");
        return new FrequencyBand(name, fMin, fMax);
    }

    /** Formats the given bands as a compact human-readable summary, e.g. for a settings display. */
    public static String describe(List<FrequencyBand> bands) {
        StringBuilder sb = new StringBuilder();
        for (FrequencyBand band : bands) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(band.name()).append(' ')
              .append((int) band.fMin()).append('-').append((int) band.fMax()).append("Hz");
        }
        return sb.toString();
    }
}
