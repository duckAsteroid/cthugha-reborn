package io.github.duckasteroid.cthugha.display;

import com.asteroid.duck.opengl.util.keys.KeyCombination;
import com.asteroid.duck.opengl.util.keys.KeyRegistry;
import io.github.duckasteroid.cthugha.config.Config;
import io.github.duckasteroid.cthugha.params.AbstractValue;
import io.github.duckasteroid.cthugha.params.NodeType;
import io.github.duckasteroid.cthugha.params.action.Action;
import io.github.duckasteroid.cthugha.params.action.ActionContext;
import io.github.duckasteroid.cthugha.params.Node;
import org.ini4j.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Reads the {@code [keys]} section of {@code cthugha.ini} and registers key bindings that
 * resolve action (and container) nodes in the parameter tree by path.
 *
 * <h2>Format</h2>
 * <pre>
 * [keys]
 * F               = Fullscreen
 * SHIFT+S         = Translate Source/Save
 * PRINT_SCREEN    = Screenshot
 * RIGHT_BRACKET   = Translate Source/Next
 * </pre>
 *
 * <p>The key spec is {@code [MOD+]*KEY} where modifiers are {@code SHIFT}, {@code CTRL},
 * {@code ALT}, {@code SUPER} and the key is a GLFW-derived name (single letter or named
 * constant with the {@code GLFW_KEY_} prefix stripped, e.g. {@code PRINT_SCREEN},
 * {@code RIGHT_BRACKET}).</p>
 *
 * <p>If the resolved node is an {@link Action}, {@link Action#execute} is called. A
 * {@code BOOLEAN} leaf is flipped deterministically (true&harr;false) rather than randomised,
 * since {@link Node#randomise} would only have a 50% chance of actually changing it. Any other
 * node type falls back to {@link Node#randomise}.</p>
 */
public class KeyBindingConfig {

    private static final Logger LOG = LoggerFactory.getLogger(KeyBindingConfig.class);
    private static final Set<String> MODIFIER_NAMES = Set.of("SHIFT", "CTRL", "CONTROL", "ALT", "SUPER");
    private static final String INI_SECTION = "keys";

    private final Node paramRoot;
    private final ActionContext actionContext;

    public KeyBindingConfig(Node paramRoot, ActionContext actionContext) {
        this.paramRoot = paramRoot;
        this.actionContext = actionContext;
    }

    /** Reads {@code [keys]} from the INI and registers all valid bindings with {@code kr}. */
    public void register(KeyRegistry kr) {
        Optional<Profile.Section> section = Config.singleton().getSection(INI_SECTION);
        if (section.isEmpty()) {
            LOG.debug("No [keys] section in cthugha.ini — no INI key bindings loaded");
            return;
        }
        section.get().forEach((keySpec, nodePath) -> {
            if (nodePath == null || nodePath.isBlank()) return;
            try {
                KeyCombination combo = parseKeyCombo(keySpec.trim());
                Optional<Node> nodeOpt = paramRoot.getChild(nodePath.trim().split("/"));
                if (nodeOpt.isEmpty()) {
                    LOG.warn("[keys] '{}' → '{}': node not found in param tree", keySpec, nodePath);
                    return;
                }
                Node target = nodeOpt.get();
                kr.registerKeyAction(combo, () -> dispatch(target), nodePath.trim());
                LOG.debug("[keys] {} → {}", keySpec.trim(), nodePath.trim());
            } catch (Exception e) {
                LOG.warn("[keys] '{}' → '{}': {}", keySpec, nodePath, e.getMessage());
            }
        });
    }

    private void dispatch(Node node) {
        if (node instanceof Action a) {
            a.execute(actionContext);
        } else if (node.getNodeType() == NodeType.BOOLEAN) {
            AbstractValue v = (AbstractValue) node;
            v.setValue(v.getValue().doubleValue() >= 0.5 ? 0 : 1);
        } else {
            node.randomise(new Random());
        }
    }

    /**
     * Parses {@code "SHIFT+PRINT_SCREEN"}, {@code "F"}, {@code "RIGHT_BRACKET"}, etc.
     * into a {@link KeyCombination}.  Tokens separated by {@code +}; modifier tokens are
     * classified by {@link #MODIFIER_NAMES}, the remaining token is the key name.
     */
    private static KeyCombination parseKeyCombo(String spec) {
        String[] tokens = spec.toUpperCase().split("\\+");
        List<String> mods = new ArrayList<>();
        String keyName = null;
        for (String token : tokens) {
            String t = token.trim();
            if (MODIFIER_NAMES.contains(t)) {
                mods.add(t);
            } else {
                if (keyName != null) {
                    throw new IllegalArgumentException("Multiple non-modifier keys in spec: " + spec);
                }
                keyName = t;
            }
        }
        if (keyName == null) {
            throw new IllegalArgumentException("No key name found in spec: " + spec);
        }
        return mods.isEmpty()
                ? KeyCombination.named(keyName)
                : KeyCombination.namedWithMods(keyName, mods.toArray(new String[0]));
    }
}
