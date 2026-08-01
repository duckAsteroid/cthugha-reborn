package io.github.duckasteroid.cthugha.video;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
    private final Map<String, String> themes;
    private final List<VideoEntry> entries;
    private final Random random = new Random();

    public VideoLibrary(Path videoDir) {
        this.videoDir = videoDir;
        VideoManifest manifest = load(videoDir.resolve("manifest.json"));
        this.themes = manifest.themes() != null ? manifest.themes() : Map.of();
        List<VideoEntry> all = manifest.videos() != null ? manifest.videos() : List.of();
        this.entries = all.stream()
                .filter(e -> Files.isRegularFile(videoDir.resolve(e.file())))
                .collect(Collectors.toList());
        int missing = all.size() - entries.size();
        if (missing > 0) {
            LOG.warn("{} video(s) listed in manifest.json have no matching file under '{}' — skipped", missing, videoDir);
        }
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
