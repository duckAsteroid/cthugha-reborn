package io.github.duckasteroid.cthugha.img;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

public class RandomImageSource {
  final Random rnd = new Random();
  private final Path imageDir;

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
