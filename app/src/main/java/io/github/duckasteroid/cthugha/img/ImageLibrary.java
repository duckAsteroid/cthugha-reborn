package io.github.duckasteroid.cthugha.img;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Overlays optional title/tags/source/license metadata (from {@code <imageDir>/manifest.json},
 * keyed by path relative to {@code imageDir}) onto the images {@link RandomImageSource} finds by
 * scanning disk. Unlike videos, most images won't have a manifest entry at all — those are
 * synthesised with filename-derived defaults, so tagging is opt-in rather than required up front.
 *
 * <p>Never throws on a missing/unparseable manifest — matches {@link RandomImageSource}'s own
 * graceful-degrade style.
 */
public class ImageLibrary {

    private static final Logger LOG = LoggerFactory.getLogger(ImageLibrary.class);

    private static final DefaultPrettyPrinter MANIFEST_PRETTY_PRINTER = new DefaultPrettyPrinter(
            Separators.createDefaultInstance().withObjectFieldValueSpacing(Separators.Spacing.AFTER))
            .withArrayIndenter(new DefaultIndenter("  ", "\n"));

    private final Path imageDir;
    private final RandomImageSource imageSource;
    private final String comment;
    private final Object writeLock = new Object();
    /** Manifest entries keyed by path relative to {@code imageDir}, e.g. {@code "nebula/nebula1.PNG"}. */
    private volatile Map<String, ImageEntry> byFile;

    public ImageLibrary(Path imageDir, RandomImageSource imageSource) {
        this.imageDir = imageDir;
        this.imageSource = imageSource;
        ImageManifest manifest = load(imageDir.resolve("manifest.json"));
        this.comment = manifest.comment();
        this.byFile = index(manifest.images() != null ? manifest.images() : List.of());
    }

    private static Map<String, ImageEntry> index(List<ImageEntry> entries) {
        Map<String, ImageEntry> map = new LinkedHashMap<>();
        for (ImageEntry e : entries) {
            map.put(e.file(), e);
        }
        return map;
    }

    private static ImageManifest load(Path manifestFile) {
        if (!Files.isRegularFile(manifestFile)) {
            return new ImageManifest(null, List.of());
        }
        try {
            return new ObjectMapper().readValue(manifestFile.toFile(), ImageManifest.class);
        } catch (IOException e) {
            LOG.warn("Failed to load image manifest {} — tags/titles disabled", manifestFile, e);
            return new ImageManifest(null, List.of());
        }
    }

    /** Every image file on disk, in sorted path order, merged with manifest metadata (filename-derived defaults where no manifest entry exists). */
    public List<ImageEntry> entries() throws IOException {
        List<Path> files = imageSource.imageFiles();
        files.sort(null);
        List<ImageEntry> result = new ArrayList<>(files.size());
        Map<String, ImageEntry> snapshot = byFile;
        for (Path p : files) {
            String rel = relativePath(p);
            result.add(snapshot.getOrDefault(rel, defaultEntry(rel)));
        }
        return result;
    }

    public Optional<ImageEntry> findByFile(String file) throws IOException {
        return entries().stream().filter(e -> e.file().equals(file)).findFirst();
    }

    private String relativePath(Path p) {
        return imageDir.relativize(p).toString().replace('\\', '/');
    }

    private static ImageEntry defaultEntry(String rel) {
        String name = rel.substring(rel.lastIndexOf('/') + 1);
        String title = name.toUpperCase().endsWith(".PNG") ? name.substring(0, name.length() - 4) : name;
        return new ImageEntry(rel, title, null, null, List.of());
    }

    /** Replaces title/tags/source/license for {@code file}'s entry (creating a manifest entry if none existed) and persists the manifest. */
    public ImageEntry updateMetadata(String file, String title, List<String> tags, String source, String license) throws IOException {
        synchronized (writeLock) {
            if (findByFile(file).isEmpty()) {
                throw new NoSuchElementException("No such image: " + file);
            }
            ImageEntry updated = new ImageEntry(file, title, source, license, tags);
            return replaceAndSave(file, updated);
        }
    }

    /**
     * Renames the image file on disk and its manifest entry (if any) to {@code newName} within
     * the same folder. {@code newName} must be a bare filename (no path separators) and must not
     * already exist.
     */
    public ImageEntry rename(String file, String newName) throws IOException {
        if (newName.isBlank() || !newName.equals(Path.of(newName).getFileName().toString())) {
            throw new IllegalArgumentException("Invalid filename: " + newName);
        }
        synchronized (writeLock) {
            ImageEntry existing = findByFile(file)
                    .orElseThrow(() -> new NoSuchElementException("No such image: " + file));
            Path oldPath = imageDir.resolve(file);
            Path newPath = oldPath.resolveSibling(newName);
            if (Files.exists(newPath)) {
                throw new FileAlreadyExistsException(newName);
            }
            Files.move(oldPath, newPath);
            String newFile = relativePath(newPath);
            ImageEntry updated = new ImageEntry(newFile, existing.title(), existing.source(), existing.license(), existing.tags());
            return replaceAndSave(file, updated);
        }
    }

    /** Must be called while holding {@code writeLock}. */
    private ImageEntry replaceAndSave(String oldFile, ImageEntry updated) throws IOException {
        Map<String, ImageEntry> next = new LinkedHashMap<>(byFile);
        next.remove(oldFile);
        next.put(updated.file(), updated);
        saveManifest(next);
        byFile = next;
        return updated;
    }

    private void saveManifest(Map<String, ImageEntry> toSave) throws IOException {
        ImageManifest manifest = new ImageManifest(comment, new ArrayList<>(toSave.values()));
        String json = new ObjectMapper().writer(MANIFEST_PRETTY_PRINTER).writeValueAsString(manifest);
        Files.writeString(imageDir.resolve("manifest.json"), json + System.lineSeparator());
    }
}
