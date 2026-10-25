package io.github.duckasteroid.cthugha.screenconfig;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class LegacyValuesTest {

    @Test
    void convertsAScreenConfigsPerFrameBeatDecayToSeconds() {
        Map<String, Object> saved = new LinkedHashMap<>();
        saved.put("Audio/Beat Detector/Threshold", 1.4);
        saved.put("Audio/Beat Detector/Decay", 1.0 / 60);
        Map<String, Object> migrated = LegacyValues.migrate(saved);
        assertEquals(Map.of("Audio/Beat Detector/Threshold", 1.4, "Audio/Beat Detector/Decay Time", 1.0),
                migrated);
    }

    @Test
    void convertsABeatPresetsRelativeDecay() {
        assertEquals(Map.of("Decay Time", 1.0 / (0.156 * 60)), LegacyValues.migrate(Map.of("Decay", 0.156)));
    }

    @Test
    void leavesCurrentValuesAndOtherDecaysAlone() {
        Map<String, Object> current = Map.of("Audio/Beat Detector/Decay Time", 0.5, "Wave/Thing/Decay", 0.3);
        assertSame(current, LegacyValues.migrate(current));
        // A value already saved under the new name wins over a leftover old one.
        Map<String, Object> both = Map.of("Decay", 0.156, "Decay Time", 2.0);
        assertEquals(2.0, LegacyValues.migrate(both).get("Decay Time"));
    }
}
