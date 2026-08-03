package io.github.duckasteroid.cthugha.beatpresets;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.duckasteroid.cthugha.params.Node;
import io.github.duckasteroid.cthugha.screenconfig.ScreenConfigParams;
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
 * Manages saved beat-detector preset snapshots on disk, mirroring {@link
 * io.github.duckasteroid.cthugha.screenconfig.ScreenConfigStore} but scoped to just the "Beat
 * Detector" subtree (band Hz ranges plus threshold/sensitivity/decay/history) rather than the
 * whole parameter tree — kept in its own directory (not {@code configs/}) so the two kinds of
 * snapshot don't show up in each other's listings.
 */
public class BeatPresetStore {

    private static final Logger LOG = LoggerFactory.getLogger(BeatPresetStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path root;

    public BeatPresetStore(Path root) {
        this.root = root;
    }

    /** Lists all saved presets by scanning {@code <root>/*.json}, sorted by display name. */
    public List<BeatPreset> list() {
        if (!Files.isDirectory(root)) return List.of();
        List<BeatPreset> result = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(root, "*.json")) {
            for (Path entry : ds) {
                if (entry.getFileName().toString().startsWith(".")) continue;
                try {
                    BeatPreset preset = MAPPER.readValue(entry.toFile(), BeatPreset.class);
                    preset.fileName = entry.getFileName().toString();
                    result.add(preset);
                } catch (IOException e) {
                    LOG.warn("Skipping malformed beat preset: {}", entry, e);
                }
            }
        } catch (IOException e) {
            LOG.warn("Cannot list beat presets under {}", root, e);
        }
        result.sort(Comparator.comparing(p -> p.name));
        return result;
    }

    /** Whether a preset named {@code displayName} (by slug) already exists on disk. */
    public boolean exists(String displayName) {
        return Files.exists(fileFor(displayName));
    }

    /**
     * Captures {@code beatDetectorRoot}'s current field values and writes it as {@code <slug>.json}.
     *
     * @param overwrite if false and a preset with this name already exists, throws {@link
     *                  PresetAlreadyExistsException} instead of silently replacing it.
     */
    public void save(String displayName, Node beatDetectorRoot, boolean overwrite) throws IOException {
        Path file = fileFor(displayName);
        if (!overwrite && Files.exists(file)) {
            throw new PresetAlreadyExistsException(displayName);
        }
        Files.createDirectories(root);
        BeatPreset preset = new BeatPreset();
        preset.name = displayName;
        preset.params = ScreenConfigParams.capture(beatDetectorRoot).values();
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), preset);
        LOG.info("Saved beat preset '{}' -> {}", displayName, file);
    }

    private Path fileFor(String displayName) {
        return root.resolve(TabParams.slugify(displayName) + ".json");
    }

    /** Thrown by {@link #save} when {@code overwrite} is false and a preset with that name already exists. */
    public static class PresetAlreadyExistsException extends IOException {
        public PresetAlreadyExistsException(String displayName) {
            super("A beat preset named '" + displayName + "' already exists");
        }
    }

    /** Applies {@code preset}'s captured field values to {@code beatDetectorRoot}. */
    public void load(BeatPreset preset, Node beatDetectorRoot) {
        Map<String, Object> params = preset.params != null ? preset.params : Map.of();
        ScreenConfigParams.apply(beatDetectorRoot, new ScreenConfigParams.Snapshot(params, Map.of()));
        LOG.info("Loaded beat preset '{}'", preset.name);
    }

    /** Deletes the on-disk file for {@code preset}. */
    public void delete(BeatPreset preset) throws IOException {
        Files.deleteIfExists(root.resolve(preset.fileName));
        LOG.info("Deleted beat preset '{}'", preset.name);
    }
}
