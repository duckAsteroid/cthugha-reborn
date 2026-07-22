package io.github.duckasteroid.cthugha.screenconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.duckasteroid.cthugha.params.DynamicChildList;
import io.github.duckasteroid.cthugha.params.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Persists the single, unnamed "current" state — a continuously-updated snapshot of the live
 * parameter tree captured via {@link ScreenConfigParams}, restored automatically the next time
 * the app launches (see issue #3).
 *
 * <p>Distinct from named {@link ScreenConfigStore} configs, which are explicit, on-demand
 * checkpoints: "current" changes constantly during a session (every animation tick, every remote
 * edit), while a named config only changes when the user explicitly hits Save. Loading a named
 * config simply overwrites "current" the same way any other live edit would (see {@link
 * ScreenConfigStore#load}) — subsequent live edits then diverge from the named config until it is
 * explicitly re-saved over.</p>
 *
 * <h2>File location</h2>
 * <p>Stored at {@code <configsRoot>/.current.json} — deliberately dot-prefixed and reserved so it
 * never shows up in {@link ScreenConfigStore#list()}'s named-config picker (which explicitly
 * skips dot-prefixed filenames; see that method).</p>
 */
public class CurrentStateStore {

    private static final Logger LOG = LoggerFactory.getLogger(CurrentStateStore.class);
    private static final String FILE_NAME = ".current.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path file;

    public CurrentStateStore(Path configsRoot) {
        this.file = configsRoot.resolve(FILE_NAME);
    }

    /** Whether a persisted "current" state file exists yet (false on a genuine first-ever launch). */
    public boolean exists() {
        return Files.isRegularFile(file);
    }

    /** Captures {@code treeRoot}'s current state as JSON, without writing it — used by the debounced writer. */
    public String serialize(Node treeRoot) throws IOException {
        ScreenConfig config = toConfig(ScreenConfigParams.capture(treeRoot));
        return MAPPER.writeValueAsString(config);
    }

    /** Writes an already-serialised snapshot (see {@link #serialize}) to the current-state file. */
    public void writeSerialized(String json) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, json);
    }

    /** Captures {@code treeRoot}'s current state and writes it immediately. */
    public void save(Node treeRoot) throws IOException {
        writeSerialized(serialize(treeRoot));
    }

    /**
     * Loads and applies the persisted "current" state onto {@code treeRoot}, if a file exists.
     *
     * @return true if a file was found (and applied); false on a first-ever launch, in which
     *         case the caller is responsible for seeding some other default state.
     */
    public boolean loadIfPresent(Node treeRoot) throws IOException {
        if (!exists()) return false;
        ScreenConfig config = MAPPER.readValue(file.toFile(), ScreenConfig.class);
        Map<String, java.util.List<DynamicChildList.ChildSpec>> dynamicChildren =
                config.dynamicChildren != null ? config.dynamicChildren : Map.of();
        ScreenConfigParams.apply(treeRoot, new ScreenConfigParams.Snapshot(config.params, dynamicChildren));
        LOG.info("Restored current state from {}", file);
        return true;
    }

    private static ScreenConfig toConfig(ScreenConfigParams.Snapshot snapshot) {
        ScreenConfig config = new ScreenConfig();
        config.name = null; // unnamed — never surfaced as a pickable config
        config.params = snapshot.values();
        config.dynamicChildren = snapshot.dynamicChildren();
        return config;
    }
}
