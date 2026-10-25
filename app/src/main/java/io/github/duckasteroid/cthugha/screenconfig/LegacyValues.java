package io.github.duckasteroid.cthugha.screenconfig;

import io.github.duckasteroid.cthugha.display.BeatDetectorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rewrites values saved under parameters that have since been renamed and re-scaled, so screen
 * configs, the auto-saved session and beat presets written by older versions still load with
 * the same effect. Applied by {@link ScreenConfigParams#apply} before any value is set.
 *
 * <ul>
 *   <li>The beat detector's per-frame {@code Decay} (fall per update, tuned at 60 fps) became
 *       {@code Decay Time} in seconds ({@link BeatDetectorConfig#decaySecondsFromPerFrame}).
 *       A preset stores it as {@code Decay}, relative to the Beat Detector node; a screen config
 *       as {@code …/Beat Detector/Decay}.</li>
 * </ul>
 */
final class LegacyValues {

    private static final Logger LOG = LoggerFactory.getLogger(LegacyValues.class);
    private static final String OLD_DECAY = "Decay";
    private static final String NEW_DECAY = "Decay Time";

    private LegacyValues() {}

    /** {@code values} with legacy entries converted; the same map if there were none. */
    static Map<String, Object> migrate(Map<String, Object> values) {
        Map<String, Object> migrated = null;
        for (Map.Entry<String, Object> e : values.entrySet()) {
            String path = e.getKey();
            if (!isLegacyDecay(path) || !(e.getValue() instanceof Number perFrame)) continue;
            String newPath = path.substring(0, path.length() - OLD_DECAY.length()) + NEW_DECAY;
            if (values.containsKey(newPath)) continue;
            if (migrated == null) migrated = new LinkedHashMap<>(values);
            double seconds = BeatDetectorConfig.decaySecondsFromPerFrame(perFrame.doubleValue());
            migrated.remove(path);
            migrated.put(newPath, seconds);
            LOG.info("Saved '{}' = {} per frame applied as '{}' = {} s", path, perFrame, newPath, seconds);
        }
        return migrated != null ? migrated : values;
    }

    private static boolean isLegacyDecay(String path) {
        return path.equals(OLD_DECAY) || path.endsWith("Beat Detector/" + OLD_DECAY);
    }
}
