package io.github.duckasteroid.cthugha.display;

import com.asteroid.duck.opengl.util.keys.Key;
import com.asteroid.duck.opengl.util.keys.KeyCombination;
import com.asteroid.duck.opengl.util.keys.KeyRegistry;
import com.asteroid.duck.opengl.util.keys.Keys;
import com.asteroid.duck.opengl.util.renderaction.RenderActionQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;

/**
 * Reads key names from stdin and dispatches them as key actions on the GL thread.
 * Format: KEY or MOD+KEY (e.g. "P", "F12", "SHIFT+T", "ESC", "SHIFT+UP").
 * Only instantiated when --stdin is passed on the command line.
 */
public class StdinKeyInjector implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(StdinKeyInjector.class);
    private static final String ACTION_TYPE = "stdin-key";

    // Common shorthand aliases → GLFW key/mod names (after stripping GLFW_KEY_/GLFW_MOD_ prefix)
    private static final Map<String, String> ALIASES = Map.of(
            "ESC",   "ESCAPE",
            "CTRL",  "CONTROL",
            "DEL",   "DELETE",
            "INS",   "INSERT",
            "PGUP",  "PAGE_UP",
            "PGDN",  "PAGE_DOWN",
            "RETURN","ENTER"
    );

    private final RenderActionQueue renderActions;
    private Thread thread;

    StdinKeyInjector(RenderActionQueue renderActions) {
        this.renderActions = renderActions;
    }

    void start(KeyRegistry keyRegistry) {
        LOG.info("stdin key injection enabled — type a key name (e.g. P, F12, ESC, SHIFT+T) and press Enter");
        thread = Thread.ofVirtual()
                .name("stdin-key-injector")
                .start(() -> {
                    try (Scanner scanner = new Scanner(System.in)) {
                        while (scanner.hasNextLine() && !Thread.currentThread().isInterrupted()) {
                            String line = scanner.nextLine().trim();
                            if (!line.isEmpty()) {
                                parseCombination(line).ifPresentOrElse(
                                        combo -> renderActions.enqueue(ACTION_TYPE, ctx -> keyRegistry.handleCallback(combo)),
                                        () -> LOG.warn("Unknown key combination: '{}'", line)
                                );
                            }
                        }
                    }
                });
    }

    /**
     * Parses "MOD+MOD+KEY" strings into a KeyCombination.
     * The last token is the key; all preceding tokens are modifiers.
     * Token lookup is case-insensitive; ALIASES are applied before lookup.
     */
    Optional<KeyCombination> parseCombination(String line) {
        String[] parts = line.toUpperCase().split("\\+");
        Keys registry = Keys.instance();

        String rawKey = parts[parts.length - 1].trim();
        Key key = registry.keyForName(ALIASES.getOrDefault(rawKey, rawKey));
        if (key == null) {
            return Optional.empty();
        }

        Set<Key> mods = new HashSet<>();
        for (int i = 0; i < parts.length - 1; i++) {
            String rawMod = parts[i].trim();
            Key mod = registry.keyForName(ALIASES.getOrDefault(rawMod, rawMod));
            if (mod == null) {
                return Optional.empty();
            }
            mods.add(mod);
        }

        return Optional.of(new KeyCombination(Set.of(key), Set.copyOf(mods)));
    }

    @Override
    public void close() {
        if (thread != null) {
            thread.interrupt();
        }
    }
}
