package io.github.duckasteroid.cthugha.screenconfig;

import io.github.duckasteroid.cthugha.display.BeatDetectorConfig;
import io.github.duckasteroid.cthugha.params.DynamicChildList.ChildSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Rewrites values saved under parameters that have since been renamed and re-scaled, so screen
 * configs, the auto-saved session and beat presets written by older versions still load with
 * the same effect. Applied by {@link ScreenConfigParams#apply} before anything is recreated or
 * set.
 *
 * <ul>
 *   <li>The beat detector's per-frame {@code Decay} (fall per update, tuned at 60 fps) became
 *       {@code Decay Time} in seconds ({@link BeatDetectorConfig#decaySecondsFromPerFrame}).
 *       A preset stores it as {@code Decay}, relative to the Beat Detector node; a screen config
 *       as {@code …/Beat Detector/Decay}.</li>
 *   <li>Transforms ({@code TransformParams}): the wave models' {@code transform} became
 *       {@code Transform}; {@code Scale} became {@code Stretch}; {@code Rotation center point}
 *       became {@code Pivot}; {@code Rotation in radians} became {@code Rotation}, in degrees.
 *       Paths are rewritten wherever they appear: value keys, binding {@code target} values, and
 *       binding names and targets in the dynamic-children specs. A continuous binding made from
 *       the remote UI is named after its target path with {@code ›} for {@code /}, so names are
 *       rewritten segment by segment too.</li>
 * </ul>
 */
final class LegacyValues {

    private static final Logger LOG = LoggerFactory.getLogger(LegacyValues.class);
    private static final String OLD_DECAY = "Decay";
    private static final String NEW_DECAY = "Decay Time";

    private static final String OLD_TRANSFORM = "transform";
    private static final String TRANSFORM = "Transform";
    private static final String OLD_ROTATION = "Rotation in radians";
    /** Old and new child names of a {@code TransformParams}: what may follow a transform segment. */
    private static final Set<String> TRANSFORM_CHILDREN = Set.of(
            "Scale", "Stretch", "Shear", "Translation", OLD_ROTATION, "Rotation",
            "Rotation center point", "Pivot", "Zoom", "Spin", "Perspective");
    private static final Map<String, String> RENAMED_TRANSFORM_CHILDREN = Map.of(
            "Scale", "Stretch",
            OLD_ROTATION, "Rotation",
            "Rotation center point", "Pivot");

    /** Separator binding names use in place of {@code /} (see {@code RemoteServer}). */
    private static final String NAME_SEPARATOR = "›";

    private LegacyValues() {}

    /** {@code values} with legacy entries converted; the same map if there were none. */
    static Map<String, Object> migrate(Map<String, Object> values) {
        Map<String, Object> migrated = new LinkedHashMap<>();
        boolean changed = false;
        for (Map.Entry<String, Object> e : values.entrySet()) {
            String path = e.getKey();
            Object value = e.getValue();
            String newPath = migrateKey(path);
            if (isOldRotation(path) && value instanceof Number radians) {
                value = Math.toDegrees(radians.doubleValue());
            }
            if (path.endsWith("/target") && value instanceof String target) {
                value = migratePath(target);
            }
            if (isLegacyDecay(path) && value instanceof Number perFrame) {
                String decayPath = path.substring(0, path.length() - OLD_DECAY.length()) + NEW_DECAY;
                if (values.containsKey(decayPath)) continue;
                double seconds = BeatDetectorConfig.decaySecondsFromPerFrame(perFrame.doubleValue());
                LOG.info("Saved '{}' = {} per frame applied as '{}' = {} s", path, perFrame, decayPath, seconds);
                newPath = decayPath;
                value = seconds;
            }
            if (!newPath.equals(path) && values.containsKey(newPath)) continue; // the new value wins
            changed |= !newPath.equals(path) || !Objects.equals(value, e.getValue());
            migrated.put(newPath, value);
        }
        if (!changed) return values;
        LOG.info("Converted saved values from older parameter names (transforms, beat decay)");
        return migrated;
    }

    /** {@code dynamicChildren} with legacy binding names and targets rewritten. */
    static Map<String, List<ChildSpec>> migrateDynamicChildren(Map<String, List<ChildSpec>> dynamicChildren) {
        Map<String, List<ChildSpec>> migrated = new LinkedHashMap<>();
        boolean changed = false;
        for (Map.Entry<String, List<ChildSpec>> e : dynamicChildren.entrySet()) {
            List<ChildSpec> specs = new ArrayList<>(e.getValue().size());
            for (ChildSpec spec : e.getValue()) {
                Map<String, String> fields = new LinkedHashMap<>(spec.fields());
                fields.computeIfPresent("target", (k, target) -> migratePath(target));
                ChildSpec next = new ChildSpec(migrateName(spec.name()), spec.type(), fields);
                changed |= !next.equals(spec);
                specs.add(next);
            }
            migrated.put(e.getKey(), specs);
        }
        return changed ? migrated : dynamicChildren;
    }

    /** A {@code /}-separated parameter path with renamed transform segments rewritten. */
    static String migratePath(String path) {
        return String.join("/", migrateSegments(path.split("/", -1)));
    }

    /** A value key: a path whose segments may include a binding name ({@code ›}-separated). */
    private static String migrateKey(String key) {
        String[] segments = key.split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            if (segments[i].contains(NAME_SEPARATOR)) segments[i] = migrateName(segments[i]);
        }
        return String.join("/", migrateSegments(segments));
    }

    private static String migrateName(String name) {
        if (!name.contains(NAME_SEPARATOR)) return name;
        return String.join(NAME_SEPARATOR, migrateSegments(name.split(NAME_SEPARATOR, -1)));
    }

    private static String[] migrateSegments(String[] segments) {
        String[] out = segments.clone();
        for (int i = 0; i + 1 < out.length; i++) {
            boolean transformNode = (out[i].equals(OLD_TRANSFORM) || out[i].equals(TRANSFORM))
                    && TRANSFORM_CHILDREN.contains(out[i + 1]);
            if (!transformNode) continue;
            out[i] = TRANSFORM;
            out[i + 1] = RENAMED_TRANSFORM_CHILDREN.getOrDefault(out[i + 1], out[i + 1]);
        }
        return out;
    }

    private static boolean isOldRotation(String path) {
        return path.endsWith("/" + OLD_ROTATION) || path.equals(OLD_ROTATION);
    }

    private static boolean isLegacyDecay(String path) {
        return path.equals(OLD_DECAY) || path.endsWith("Beat Detector/" + OLD_DECAY);
    }
}
