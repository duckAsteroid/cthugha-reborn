package io.github.duckasteroid.cthugha.screenconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.duckasteroid.cthugha.params.DynamicChildList;
import io.github.duckasteroid.cthugha.params.Node;
import io.github.duckasteroid.cthugha.tab.TabParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Manages saved "screen config" snapshots on disk.
 *
 * <h2>Directory layout</h2>
 * <pre>
 * &lt;root&gt;/
 *   my_favourite.json
 *   chill_mode.json
 * </pre>
 *
 * <p>Each file holds a {@link ScreenConfig}: a display name and a flat, order-preserving
 * path→value map captured by {@link ScreenConfigParams#capture}. There is no resolution-bound
 * binary cache — {@link #load} simply replays the captured values against the live tree, and
 * any tab generator selected by the snapshot regenerates its own translation map (via the
 * existing generator-selection machinery) at whatever resolution is current.</p>
 */
public class ScreenConfigStore {

    private static final Logger LOG = LoggerFactory.getLogger(ScreenConfigStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path root;

    public ScreenConfigStore(Path root) {
        this.root = root;
    }

    /**
     * Lists all saved configs by scanning {@code <root>/*.json}, sorted by display name.
     * Dot-prefixed files (e.g. {@code .current.json}, the continuously-persisted "current" state
     * — see {@link CurrentStateStore}) are reserved/internal and always excluded, since Java NIO's
     * glob matching (unlike a shell glob) does <em>not</em> skip them on its own.
     */
    public List<ScreenConfig> list() {
        if (!Files.isDirectory(root)) return List.of();
        List<ScreenConfig> result = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(root, "*.json")) {
            for (Path entry : ds) {
                if (entry.getFileName().toString().startsWith(".")) continue;
                try {
                    ScreenConfig config = MAPPER.readValue(entry.toFile(), ScreenConfig.class);
                    config.fileName = entry.getFileName().toString();
                    result.add(config);
                } catch (IOException e) {
                    LOG.warn("Skipping malformed screen config: {}", entry, e);
                }
            }
        } catch (IOException e) {
            LOG.warn("Cannot list screen configs under {}", root, e);
        }
        result.sort(Comparator.comparing(c -> c.name));
        return result;
    }

    /** Whether a config named {@code displayName} (by slug) already exists on disk. */
    public boolean exists(String displayName) {
        return Files.exists(fileFor(displayName));
    }

    /**
     * Captures {@code treeRoot}'s current state and writes it as {@code <slug>.json}.
     *
     * @param overwrite if false and a config with this name already exists, throws {@link
     *                  ConfigAlreadyExistsException} instead of silently replacing it — callers
     *                  should surface this to the user and re-call with {@code overwrite=true}
     *                  once they've confirmed.
     */
    public void save(String displayName, Node treeRoot, boolean overwrite) throws IOException {
        Path file = fileFor(displayName);
        if (!overwrite && Files.exists(file)) {
            throw new ConfigAlreadyExistsException(displayName);
        }
        Files.createDirectories(root);
        ScreenConfig config = new ScreenConfig();
        config.name = displayName;
        ScreenConfigParams.Snapshot snapshot = ScreenConfigParams.capture(treeRoot);
        config.params = snapshot.values();
        config.dynamicChildren = snapshot.dynamicChildren();
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), config);
        LOG.info("Saved screen config '{}' -> {}", displayName, file);
    }

    private Path fileFor(String displayName) {
        return root.resolve(TabParams.slugify(displayName) + ".json");
    }

    /** Thrown by {@link #save} when {@code overwrite} is false and a config with that name already exists. */
    public static class ConfigAlreadyExistsException extends IOException {
        public ConfigAlreadyExistsException(String displayName) {
            super("A config named '" + displayName + "' already exists");
        }
    }

    /** Applies {@code config}'s captured params to {@code treeRoot}. */
    public void load(ScreenConfig config, Node treeRoot) {
        Map<String, List<DynamicChildList.ChildSpec>> dynamicChildren =
                config.dynamicChildren != null ? config.dynamicChildren : Map.of();
        ScreenConfigParams.apply(treeRoot, new ScreenConfigParams.Snapshot(config.params, dynamicChildren));
        LOG.info("Loaded screen config '{}'", config.name);
    }

    /** Deletes the on-disk file for {@code config}. */
    public void delete(ScreenConfig config) throws IOException {
        Files.deleteIfExists(root.resolve(config.fileName));
        LOG.info("Deleted screen config '{}'", config.name);
    }
}
