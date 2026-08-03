package io.github.duckasteroid.cthugha.video;

import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Loads {@code <videoDir>/manifest.json} (title/tags/source/license per video, plus a
 * {@code themes} id→description dictionary) and generates/caches thumbnail PNGs on demand.
 *
 * <p>Never throws — a missing or unparseable manifest just yields an empty library (logged as a
 * warning), matching {@code RandomQuoteSource}'s graceful-degrade style, so every caller can use
 * this as a plain field initializer with no try/catch.
 */
public class VideoLibrary {

    private static final Logger LOG = LoggerFactory.getLogger(VideoLibrary.class);

    private final Path videoDir;
    private final String comment;
    private final Map<String, String> themes;
    private final Object writeLock = new Object();
    /** Every manifest entry, including ones whose file is currently missing — the source of truth written back to disk. */
    private volatile List<VideoEntry> allEntries;
    /** {@code allEntries} filtered to those with a file actually present on disk, in manifest order. */
    private volatile List<VideoEntry> entries;
    private final Random random = new Random();

    public VideoLibrary(Path videoDir) {
        this.videoDir = videoDir;
        VideoManifest manifest = load(videoDir.resolve("manifest.json"));
        this.comment = manifest.comment();
        this.themes = manifest.themes() != null ? manifest.themes() : Map.of();
        this.allEntries = new ArrayList<>(manifest.videos() != null ? manifest.videos() : List.of());
        this.entries = fileBacked(allEntries);
        int missing = allEntries.size() - entries.size();
        if (missing > 0) {
            LOG.warn("{} video(s) listed in manifest.json have no matching file under '{}' — skipped", missing, videoDir);
        }
    }

    private List<VideoEntry> fileBacked(List<VideoEntry> source) {
        return source.stream()
                .filter(e -> Files.isRegularFile(videoDir.resolve(e.file())))
                .collect(Collectors.toList());
    }

    private static VideoManifest load(Path manifestFile) {
        if (!Files.isRegularFile(manifestFile)) {
            return new VideoManifest(Map.of(), List.of());
        }
        try {
            return new ObjectMapper().readValue(manifestFile.toFile(), VideoManifest.class);
        } catch (IOException e) {
            LOG.warn("Failed to load video manifest {} — Videos tab disabled", manifestFile, e);
            return new VideoManifest(Map.of(), List.of());
        }
    }

    /** All videos with a manifest entry and a file on disk, in manifest order. */
    public List<VideoEntry> entries() {
        return entries;
    }

    public Path pathOf(VideoEntry entry) {
        return videoDir.resolve(entry.file());
    }

    public Optional<VideoEntry> findByFile(String file) {
        return entries.stream().filter(e -> e.file().equals(file)).findFirst();
    }

    /** The first of the entry's tags that's a known theme id, or {@code ""} if none match. */
    public String primaryTheme(VideoEntry entry) {
        return entry.tags().stream().filter(themes::containsKey).findFirst().orElse("");
    }

    public VideoEntry random() {
        return entries.get(random.nextInt(entries.size()));
    }

    /** Replaces title/tags/source/license/defaultChapter for {@code file}'s entry and persists the manifest. Chapters, file and durationSeconds are left untouched. */
    public VideoEntry updateMetadata(String file, String title, List<String> tags, String source, String license, String defaultChapter) throws IOException {
        synchronized (writeLock) {
            VideoEntry existing = requireEntry(file);
            VideoEntry updated = new VideoEntry(existing.file(), title, source, license, tags,
                    existing.durationSeconds(), existing.chapters(), defaultChapter);
            return replaceAndSave(file, updated);
        }
    }

    /** Appends a new named chapter. Throws if {@code file} is unknown or already has a chapter named {@code name}. */
    public VideoEntry addChapter(String file, String name, double start, double end) throws IOException {
        synchronized (writeLock) {
            VideoEntry existing = requireEntry(file);
            if (existing.chapters().stream().anyMatch(c -> c.name().equals(name))) {
                throw new IllegalStateException("Chapter already exists: " + name);
            }
            List<VideoEntry.Chapter> chapters = new ArrayList<>(existing.chapters());
            chapters.add(new VideoEntry.Chapter(name, start, end));
            return replaceAndSave(file, withChapters(existing, chapters, existing.defaultChapter()));
        }
    }

    /**
     * Updates the chapter named {@code name} on {@code file}'s entry — any of {@code newName}/
     * {@code start}/{@code end} left {@code null} keeps its current value. Renaming a chapter that
     * is the entry's current {@code defaultChapter} keeps the pointer valid. Throws if the file or
     * chapter doesn't exist.
     */
    public VideoEntry updateChapter(String file, String name, String newName, Double start, Double end) throws IOException {
        synchronized (writeLock) {
            VideoEntry existing = requireEntry(file);
            List<VideoEntry.Chapter> chapters = new ArrayList<>(existing.chapters());
            int idx = indexOfChapter(chapters, name);
            if (idx < 0) {
                throw new NoSuchElementException("No such chapter: " + name);
            }
            VideoEntry.Chapter current = chapters.get(idx);
            String resolvedName = newName != null ? newName : current.name();
            chapters.set(idx, new VideoEntry.Chapter(resolvedName,
                    start != null ? start : current.start(), end != null ? end : current.end()));
            String defaultChapter = name.equals(existing.defaultChapter()) ? resolvedName : existing.defaultChapter();
            return replaceAndSave(file, withChapters(existing, chapters, defaultChapter));
        }
    }

    /** Removes the chapter named {@code name} from {@code file}'s entry, clearing defaultChapter if it pointed at it. Throws if the file or chapter doesn't exist. */
    public VideoEntry deleteChapter(String file, String name) throws IOException {
        synchronized (writeLock) {
            VideoEntry existing = requireEntry(file);
            List<VideoEntry.Chapter> chapters = new ArrayList<>(existing.chapters());
            if (indexOfChapter(chapters, name) < 0) {
                throw new NoSuchElementException("No such chapter: " + name);
            }
            chapters.removeIf(c -> c.name().equals(name));
            String defaultChapter = name.equals(existing.defaultChapter()) ? null : existing.defaultChapter();
            return replaceAndSave(file, withChapters(existing, chapters, defaultChapter));
        }
    }

    private static int indexOfChapter(List<VideoEntry.Chapter> chapters, String name) {
        for (int i = 0; i < chapters.size(); i++) {
            if (chapters.get(i).name().equals(name)) return i;
        }
        return -1;
    }

    private static VideoEntry withChapters(VideoEntry e, List<VideoEntry.Chapter> chapters, String defaultChapter) {
        return new VideoEntry(e.file(), e.title(), e.source(), e.license(), e.tags(),
                e.durationSeconds(), chapters, defaultChapter);
    }

    /**
     * Renames the video file and its thumbnail sidecar on disk, and the manifest entry, to
     * {@code newFile}. {@code newFile} must be a bare filename (no path separators) and must not
     * already exist.
     */
    public VideoEntry rename(String file, String newFile) throws IOException {
        if (newFile.isBlank() || !newFile.equals(Path.of(newFile).getFileName().toString())) {
            throw new IllegalArgumentException("Invalid filename: " + newFile);
        }
        synchronized (writeLock) {
            VideoEntry existing = requireEntry(file);
            Path oldPath = videoDir.resolve(file);
            Path newPath = videoDir.resolve(newFile);
            if (Files.exists(newPath)) {
                throw new FileAlreadyExistsException(newFile);
            }
            Files.move(oldPath, newPath);
            Path oldThumb = videoDir.resolve(file + ".png");
            if (Files.exists(oldThumb)) {
                Files.move(oldThumb, videoDir.resolve(newFile + ".png"));
            }
            VideoEntry updated = new VideoEntry(newFile, existing.title(), existing.source(), existing.license(),
                    existing.tags(), existing.durationSeconds(), existing.chapters(), existing.defaultChapter());
            return replaceAndSave(file, updated);
        }
    }

    private VideoEntry requireEntry(String file) {
        return allEntries.stream().filter(e -> e.file().equals(file)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("No such video: " + file));
    }

    /** Must be called while holding {@code writeLock}. */
    private VideoEntry replaceAndSave(String oldFile, VideoEntry updated) throws IOException {
        List<VideoEntry> next = new ArrayList<>(allEntries);
        next.replaceAll(e -> e.file().equals(oldFile) ? updated : e);
        saveManifest(next);
        allEntries = next;
        entries = fileBacked(next);
        return updated;
    }

    /**
     * Matches the hand-authored manifest's style closely enough to keep re-saved diffs readable —
     * no space before the colon (Jackson's {@code writerWithDefaultPrettyPrinter()} default adds
     * one), and every array element (including nested objects) on its own line.
     */
    private static final PrettyPrinter MANIFEST_PRETTY_PRINTER = new DefaultPrettyPrinter(
            Separators.createDefaultInstance().withObjectFieldValueSpacing(Separators.Spacing.AFTER))
            .withArrayIndenter(new DefaultIndenter("  ", "\n"));

    private void saveManifest(List<VideoEntry> toSave) throws IOException {
        VideoManifest manifest = new VideoManifest(comment, themes, toSave);
        String json = new ObjectMapper().writer(MANIFEST_PRETTY_PRINTER).writeValueAsString(manifest);
        Files.writeString(videoDir.resolve("manifest.json"), json + System.lineSeparator());
    }

    /**
     * Returns the {@code <file>.png} thumbnail sidecar next to the video, generating it first if
     * it doesn't exist yet. Videos are treated as static once placed, so — unlike the palette
     * preview cache — there's no staleness re-check against the source file once a thumbnail has
     * been generated once.
     */
    public Path thumbnailFile(VideoEntry entry, int maxDim) throws IOException {
        Path thumb = videoDir.resolve(entry.file() + ".png");
        if (!Files.exists(thumb)) {
            generateThumbnail(pathOf(entry), thumb, maxDim);
        }
        return thumb;
    }

    private static void generateThumbnail(Path video, Path out, int maxDim) throws IOException {
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(video.toFile());
        grabber.setPixelFormat(avutil.AV_PIX_FMT_RGBA);
        try {
            grabber.start();
            // Seek a little way in so the thumbnail isn't a black/blank opening frame.
            long lengthUs = grabber.getLengthInTime();
            if (lengthUs > 0) {
                grabber.setTimestamp(lengthUs / 10);
            }
            Frame frame = grabber.grabImage();
            if (frame == null) {
                // A seek can land just before a clean decoded frame; one retry is enough here.
                frame = grabber.grabImage();
            }
            if (frame == null) {
                throw new IOException("No frame decoded for thumbnail: " + video);
            }
            BufferedImage scaled = scale(toBufferedImage(frame), maxDim);
            ImageIO.write(scaled, "png", out.toFile());
        } catch (FrameGrabber.Exception e) {
            throw new IOException("Failed to generate thumbnail for " + video, e);
        } finally {
            try {
                grabber.stop();
                grabber.release();
            } catch (FrameGrabber.Exception e) {
                LOG.warn("Error closing thumbnail grabber for {}", video, e);
            }
        }
    }

    /** Packs an RGBA-format decoded frame into a {@code TYPE_INT_ARGB} image, row by row. */
    private static BufferedImage toBufferedImage(Frame frame) {
        int width = frame.imageWidth;
        int height = frame.imageHeight;
        int stride = frame.imageStride;
        ByteBuffer src = (ByteBuffer) frame.image[0];
        ((Buffer) src).clear();

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            src.position(y * stride);
            for (int x = 0; x < width; x++) {
                int r = src.get() & 0xFF;
                int g = src.get() & 0xFF;
                int b = src.get() & 0xFF;
                int a = src.get() & 0xFF;
                row[x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
            img.setRGB(0, y, width, 1, row, 0, width);
        }
        return img;
    }

    private static BufferedImage scale(BufferedImage src, int maxDim) {
        int w = src.getWidth();
        int h = src.getHeight();
        double factor = Math.min(1.0, (double) maxDim / Math.max(w, h));
        int scaledWidth = Math.max(1, (int) Math.round(w * factor));
        int scaledHeight = Math.max(1, (int) Math.round(h * factor));
        BufferedImage scaled = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, scaledWidth, scaledHeight, null);
        } finally {
            g.dispose();
        }
        return scaled;
    }
}
