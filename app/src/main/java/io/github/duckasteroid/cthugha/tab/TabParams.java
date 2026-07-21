package io.github.duckasteroid.cthugha.tab;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.duckasteroid.cthugha.params.AbstractValue;
import io.github.duckasteroid.cthugha.params.Node;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * Static utilities for capturing, applying, and checksumming {@link TabGenerator} params.
 *
 * <p>Param paths are slash-delimited node names relative to the generator root
 * (e.g. {@code "Delta R"} for a direct child, {@code "Sub/Value"} for nested nodes).
 * Paths are the keys in the flat map stored in {@link TabConfig#params}.</p>
 */
public class TabParams {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Walks the node tree of {@code source} and returns a sorted flat map of
     * path → current value for every leaf {@link AbstractValue}.
     */
    public static Map<String, Number> capture(TabGenerator source) {
        TreeMap<String, Number> result = new TreeMap<>();
        walkCapture(source, "", result);
        return result;
    }

    private static void walkCapture(Node node, String prefix, Map<String, Number> out) {
        if (node instanceof AbstractValue av) {
            out.put(prefix, av.getValue());
        } else {
            node.getChildren().forEach(child -> {
                String path = prefix.isEmpty() ? child.getName() : prefix + "/" + child.getName();
                walkCapture(child, path, out);
            });
        }
    }

    /**
     * Applies a saved param map to {@code target} by matching paths to leaf nodes and
     * calling {@link AbstractValue#setValue(Number)}.  Unrecognised paths are silently ignored.
     */
    public static void apply(TabGenerator target, Map<String, Number> params) {
        walkApply(target, "", params);
    }

    private static void walkApply(Node node, String prefix, Map<String, Number> params) {
        if (node instanceof AbstractValue av) {
            Number v = params.get(prefix);
            if (v != null) av.setValue(v);
        } else {
            node.getChildren().forEach(child -> {
                String path = prefix.isEmpty() ? child.getName() : prefix + "/" + child.getName();
                walkApply(child, path, params);
            });
        }
    }

    /**
     * Computes SHA-256(generatorFQN + canonical sorted-key params JSON) and returns it as a hex string.
     *
     * <p>The FQN is included so configs for different generators with identical param names
     * never collide.  Uses a {@link TreeMap} to guarantee key ordering before serialising.</p>
     */
    public static String checksum(String generatorFqn, Map<String, Number> params) {
        try {
            String paramsJson = MAPPER.writeValueAsString(new TreeMap<>(params));
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((generatorFqn + paramsJson).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        } catch (Exception e) {
            throw new RuntimeException("checksum computation failed", e);
        }
    }

    /**
     * Converts a display name to a filesystem-safe slug: lowercase, runs of
     * non-alphanumeric characters replaced by a single underscore, leading/trailing
     * underscores stripped.
     */
    public static String slugify(String name) {
        return name.toLowerCase()
                   .replaceAll("[^a-z0-9]+", "_")
                   .replaceAll("^_+|_+$", "");
    }
}
