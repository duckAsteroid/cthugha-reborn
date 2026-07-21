package io.github.duckasteroid.cthugha.img;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RandomImageSource {
  final Random rnd = new Random();
  private final Path imageDir;
  private final Map<Path, CachedThumbnail> thumbnailCache = new ConcurrentHashMap<>();

  private record CachedThumbnail(long mtimeMillis, byte[] pngBytes) {}

  public RandomImageSource(Path imageDir) {
    this.imageDir = imageDir;
  }

  public List<Path> imageFiles() throws IOException {
    try (var stream = Files.walk(imageDir)) {
      return stream
        .filter(Files::isRegularFile)
        .filter(f -> f.toString().toUpperCase().endsWith(".PNG"))
        .collect(Collectors.toList());
    }
  }

  /**
   * The image's theme folder, i.e. the name of its parent directory relative to the image
   * root, or {@code ""} if the image sits directly in the root with no theme folder.
   */
  public String groupOf(Path image) {
    Path parent = imageDir.relativize(image).getParent();
    return parent == null ? "" : parent.toString();
  }

  public BufferedImage loadImage(Path image) throws IOException {
    BufferedImage img = ImageIO.read(image.toFile());
    if (img == null) {
      throw new IOException("Unsupported or corrupt image: " + image);
    }
    return img;
  }

  public BufferedImage nextImage() throws IOException {
    List<Path> paths = imageFiles();
    return loadImage(paths.get(rnd.nextInt(paths.size())));
  }

  /**
   * Returns a PNG-encoded thumbnail of {@code image}, scaled so its longest side is at most
   * {@code maxDim} pixels. Thumbnails are cached in memory keyed by file modification time, so
   * repeat requests (e.g. the remote UI's image grid) skip re-decoding and re-scaling.
   */
  public byte[] loadThumbnail(Path image, int maxDim) throws IOException {
    long mtimeMillis = Files.getLastModifiedTime(image).toMillis();
    CachedThumbnail cached = thumbnailCache.get(image);
    if (cached != null && cached.mtimeMillis() == mtimeMillis) {
      return cached.pngBytes();
    }
    BufferedImage scaled = scale(loadImage(image), maxDim);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(scaled, "png", out);
    byte[] pngBytes = out.toByteArray();
    thumbnailCache.put(image, new CachedThumbnail(mtimeMillis, pngBytes));
    return pngBytes;
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

  /** Strips the {@code .PNG} extension and upper-cases the remainder, e.g. {@code "nebula1"} → {@code "NEBULA1"}. */
  public static String displayName(Path path) {
    String fn = path.getFileName().toString().toUpperCase();
    return fn.endsWith(".PNG") ? fn.substring(0, fn.length() - 4) : fn;
  }

  /** Finds the image file whose {@link #displayName(Path)} matches {@code name}, case-insensitively. */
  public Optional<Path> findByDisplayName(String name) throws IOException {
    return imageFiles().stream()
      .filter(p -> displayName(p).equalsIgnoreCase(name))
      .findFirst();
  }
}
