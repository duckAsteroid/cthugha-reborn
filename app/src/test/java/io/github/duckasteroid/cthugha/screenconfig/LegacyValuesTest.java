package io.github.duckasteroid.cthugha.screenconfig;

import org.junit.jupiter.api.Test;

import io.github.duckasteroid.cthugha.params.DynamicChildList;

import java.util.LinkedHashMap;
import java.util.List;
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

    @Test
    void renamesTransformLeavesAndConvertsRadiansToDegrees() {
        Map<String, Object> saved = new LinkedHashMap<>();
        saved.put("Wave/Oscilloscope 4/transform/Rotation in radians", Math.PI / 2);
        saved.put("Wave/Oscilloscope 4/transform/Rotation center point/X", 0.25);
        saved.put("Wave/Oscilloscope 4/transform/Scale/Y", 2.0);
        saved.put("Wave/Oscilloscope 4/transform/Translation/X", 0.1);
        saved.put("Videos/Playback/Transform/Rotation in radians", Math.PI);
        saved.put("Quotes/Transform/Scale/X", 1.5);
        Map<String, Object> migrated = LegacyValues.migrate(saved);
        assertEquals(90.0, (Double) migrated.get("Wave/Oscilloscope 4/Transform/Rotation"), 1e-9);
        assertEquals(0.25, migrated.get("Wave/Oscilloscope 4/Transform/Pivot/X"));
        assertEquals(2.0, migrated.get("Wave/Oscilloscope 4/Transform/Stretch/Y"));
        assertEquals(0.1, migrated.get("Wave/Oscilloscope 4/Transform/Translation/X"));
        assertEquals(180.0, (Double) migrated.get("Videos/Playback/Transform/Rotation"), 1e-9);
        assertEquals(1.5, migrated.get("Quotes/Transform/Stretch/X"));
        assertEquals(6, migrated.size());
    }

    @Test
    void rewritesBindingNamesAndTargetsPointingAtOldPaths() {
        // A remote-UI-made binding is named after its target path with › for /.
        Map<String, Object> saved = new LinkedHashMap<>();
        saved.put("Bindings/Wave›RadialSpectrum 1›transform›Rotation in radians/script", "sine(0.05)");
        saved.put("Bindings/Wave›RadialSpectrum 1›transform›Rotation in radians/target",
                "Wave/RadialSpectrum 1/transform/Rotation in radians");
        saved.put("Bindings/osc rotation/target", "Wave/Oscilloscope 1/transform/Rotation in radians");
        saved.put("Bindings/Trigger 1/target", "Images/Random");
        Map<String, Object> migrated = LegacyValues.migrate(saved);
        assertEquals(Map.of(
                "Bindings/Wave›RadialSpectrum 1›Transform›Rotation/script", "sine(0.05)",
                "Bindings/Wave›RadialSpectrum 1›Transform›Rotation/target", "Wave/RadialSpectrum 1/Transform/Rotation",
                "Bindings/osc rotation/target", "Wave/Oscilloscope 1/Transform/Rotation",
                "Bindings/Trigger 1/target", "Images/Random"), migrated);

        Map<String, List<DynamicChildList.ChildSpec>> specs = Map.of("Bindings", List.of(
                new DynamicChildList.ChildSpec("Wave›RadialSpectrum 1›transform›Rotation in radians", "CONTINUOUS",
                        Map.of("script", "sine(0.05)", "target", "Wave/RadialSpectrum 1/transform/Rotation in radians")),
                new DynamicChildList.ChildSpec("Trigger 1", "EDGE_TRIGGERED", Map.of("target", "Images/Random"))));
        List<DynamicChildList.ChildSpec> out = LegacyValues.migrateDynamicChildren(specs).get("Bindings");
        assertEquals(new DynamicChildList.ChildSpec("Wave›RadialSpectrum 1›Transform›Rotation", "CONTINUOUS",
                Map.of("script", "sine(0.05)", "target", "Wave/RadialSpectrum 1/Transform/Rotation")), out.get(0));
        assertEquals(specs.get("Bindings").get(1), out.get(1));
    }

    @Test
    void leavesUnrelatedPathsNamedTransformOrScaleAlone() {
        Map<String, Object> other = Map.of("Tab/Translate Source/Scale/X", 0.5, "Render/Blur/Enabled", 1);
        assertSame(other, LegacyValues.migrate(other));
        Map<String, List<DynamicChildList.ChildSpec>> none = Map.of("Wave", List.of(
                new DynamicChildList.ChildSpec("Oscilloscope 1", "OSCILLOSCOPE", Map.of())));
        assertSame(none, LegacyValues.migrateDynamicChildren(none));
    }
}
