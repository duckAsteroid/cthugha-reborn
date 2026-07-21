package io.github.duckasteroid.cthugha.tab;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Dimension;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Manages saved translation presets on disk.
 *
 * <h2>Directory layout</h2>
 * <pre>
 * &lt;root&gt;/
 *   Spiral/
 *     cool_spiral/
 *       config.json          — name, checksum, flat param map
 *       1024x768.tab         — 32-byte SHA-256 + 4-byte LE width + 4-byte LE height + RG16UI data
 *     another_preset/
 *       config.json
 *   Hurricane/
 *     tight_spin/
 *       config.json
 *       1920x1080.tab
 * </pre>
 *
 * <h2>Binary {@code .tab} format</h2>
 * All integers are little-endian (x86 native).
 * <pre>
 *   [32 bytes] SHA-256 checksum (raw bytes matching config.json checksum)
 *   [ 4 bytes] width  (LE int)
 *   [ 4 bytes] height (LE int)
 *   [ N bytes] RG16UI pixel data: (srcX, srcY) as LE uint16 pairs, row-major
 *              N = width * height * 4
 * </pre>
 *
 * <p>On load, the checksum in the {@code .tab} header is compared against {@link TabConfig#checksum}.
 * A mismatch (or absent file) triggers regeneration via {@link TabGenerator#generate}
 * and the new bytes are written back.</p>
 */
public class TabStore {

    private static final Logger LOG = LoggerFactory.getLogger(TabStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path root;

    public TabStore(Path root) {
        this.root = root;
    }

    // -------------------------------------------------------------------------
    // Enumeration
    // -------------------------------------------------------------------------

    /**
     * Lists all saved presets for {@code source} by scanning
     * {@code <root>/<SimpleName>/}/&#42;{@code /config.json}.
     */
    public List<TabConfig> listFor(TabGenerator source) {
        Path dir = sourceDir(source);
        if (!Files.isDirectory(dir)) return List.of();
        List<TabConfig> result = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path entry : ds) {
                if (!Files.isDirectory(entry)) continue;
                Path cfg = entry.resolve("config.json");
                if (!Files.exists(cfg)) continue;
                try {
                    TabConfig config = MAPPER.readValue(cfg.toFile(), TabConfig.class);
                    config.folderName = entry.getFileName().toString();
                    result.add(config);
                } catch (IOException e) {
                    LOG.warn("Skipping malformed config: {}", cfg, e);
                }
            }
        } catch (IOException e) {
            LOG.warn("Cannot list presets under {}", dir, e);
        }
        result.sort(Comparator.comparing(c -> c.name));
        return result;
    }

    /**
     * Returns all presets grouped by canonical {@link TabGenerator} instance,
     * preserving the order of sources in {@link GeneratorRegistry}.
     */
    public Map<TabGenerator, List<TabConfig>> listAll(GeneratorRegistry rts) {
        Map<TabGenerator, List<TabConfig>> result = new LinkedHashMap<>();
        rts.getSources().forEach(src -> result.put(src, listFor(src)));
        return result;
    }

    // -------------------------------------------------------------------------
    // Save
    // -------------------------------------------------------------------------

    /**
     * Captures the current params of {@code source}, writes {@code config.json}, and writes a
     * {@code .tab} binary from the {@code currentTranslate} buffer that is already on screen.
     *
     * @param source           the active canonical generator
     * @param displayName      human-readable name (slugified for the folder)
     * @param currentTranslate the {@link TabBuffer} currently displayed
     * @param resolution       the render resolution (determines the {@code .tab} filename)
     */
    public void save(TabGenerator source, String displayName,
                     TabBuffer currentTranslate, Dimension resolution) throws IOException {
        Map<String, Number> params = TabParams.capture(source);
        String checksum = TabParams.checksum(source.getClass().getName(), params);
        String slug = TabParams.slugify(displayName);

        Path dir = sourceDir(source).resolve(slug);
        Files.createDirectories(dir);

        TabConfig config = new TabConfig();
        config.name = displayName;
        config.checksum = checksum;
        config.params = params;
        MAPPER.writerWithDefaultPrettyPrinter()
              .writeValue(dir.resolve("config.json").toFile(), config);

        writeTabFile(currentTranslate, checksum, dir, resolution);
        LOG.info("Saved preset '{}' → {}", displayName, dir);
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    /**
     * Deletes the preset folder for {@code config} (all {@code .tab} files and
     * {@code config.json}), then removes the now-empty directory.
     */
    public void delete(TabConfig config, TabGenerator source) throws IOException {
        Path dir = sourceDir(source).resolve(config.folderName);
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.delete(p); } catch (IOException e) { LOG.warn("Could not delete {}", p, e); }
            });
        }
        LOG.info("Deleted preset '{}' from {}", config.name, dir);
    }

    // -------------------------------------------------------------------------
    // Load
    // -------------------------------------------------------------------------

    /**
     * Applies {@code config} params to {@code source} and returns a ready {@link TabBuffer}.
     *
     * <p>If a {@code .tab} file exists for {@code resolution} and its checksum matches
     * {@link TabConfig#checksum}, it is loaded directly.  Otherwise the tab is regenerated
     * via {@link TabGenerator#generate} and the result is written to disk for future loads.</p>
     *
     * @param config     preset to load (must have {@link TabConfig#folderName} set)
     * @param source     the matching canonical generator instance
     * @param resolution desired render resolution
     * @param rng        random source (used only when regeneration is needed)
     */
    public TabBuffer load(TabConfig config, TabGenerator source,
                          Dimension resolution, Random rng) throws IOException {
        TabParams.apply(source, config.params);

        Path dir = sourceDir(source).resolve(config.folderName);
        Path tabPath = tabFilePath(dir, resolution);

        if (Files.exists(tabPath)) {
            String storedChecksum = readTabChecksum(tabPath);
            if (storedChecksum.equals(config.checksum)) {
                LOG.info("Loading cached .tab: {}", tabPath);
                return readTabFile(tabPath);
            }
            LOG.info("Checksum mismatch for {} — regenerating", tabPath);
        } else {
            LOG.info("No .tab for {}x{} — generating", resolution.width, resolution.height);
        }

        TabMapping mapper = source.generate(resolution.width, resolution.height, rng);
        TabBuffer translate = new TabBuffer(resolution);
        translate.fill(mapper, rng);
        Files.createDirectories(dir);
        writeTabFile(translate, config.checksum, dir, resolution);
        return translate;
    }

    // -------------------------------------------------------------------------
    // Binary .tab I/O
    // -------------------------------------------------------------------------

    private void writeTabFile(TabBuffer translate, String checksum,
                              Path dir, Dimension resolution) throws IOException {
        Path out = tabFilePath(dir, resolution);
        byte[] checksumBytes = HexFormat.of().parseHex(checksum);

        ByteBuffer buf = translate.getBuffer();
        buf.rewind();
        byte[] pixelData = new byte[buf.remaining()];
        buf.get(pixelData);
        buf.rewind();

        try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(out))) {
            os.write(checksumBytes);
            os.write(intToLE(resolution.width));
            os.write(intToLE(resolution.height));
            os.write(pixelData);
        }
    }

    private String readTabChecksum(Path tabPath) throws IOException {
        try (InputStream is = Files.newInputStream(tabPath)) {
            return HexFormat.of().formatHex(is.readNBytes(32));
        }
    }

    private TabBuffer readTabFile(Path tabPath) throws IOException {
        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(tabPath)))) {
            dis.readNBytes(32); // checksum already verified by caller
            int w = readIntLE(dis);
            int h = readIntLE(dis);
            byte[] data = dis.readNBytes(w * h * 4);
            return TabBuffer.fromBytes(w, h, data);
        }
    }

    // -------------------------------------------------------------------------
    // Path helpers
    // -------------------------------------------------------------------------

    private Path sourceDir(TabGenerator source) {
        return root.resolve(source.getClass().getSimpleName());
    }

    private Path tabFilePath(Path dir, Dimension resolution) {
        return dir.resolve(resolution.width + "x" + resolution.height + ".tab");
    }

    // -------------------------------------------------------------------------
    // LE integer encoding
    // -------------------------------------------------------------------------

    private static byte[] intToLE(int v) {
        return new byte[]{
            (byte) (v         & 0xFF),
            (byte) ((v >>  8) & 0xFF),
            (byte) ((v >> 16) & 0xFF),
            (byte) ((v >> 24) & 0xFF)
        };
    }

    private static int readIntLE(DataInputStream dis) throws IOException {
        byte[] b = dis.readNBytes(4);
        return (b[0] & 0xFF)
             | ((b[1] & 0xFF) <<  8)
             | ((b[2] & 0xFF) << 16)
             | ((b[3] & 0xFF) << 24);
    }
}
