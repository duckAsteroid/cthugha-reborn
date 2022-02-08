package io.github.duckasteroid.cthugha.img;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.common.bytesource.ByteSourceInputStream;
import org.apache.commons.imaging.formats.pcx.PcxImageParser;

public class RandomImageSource {
  final Random rnd = new Random();
  private final Path imageDir;

  public RandomImageSource(Path imageDir) {
    this.imageDir = imageDir;
  }

  public List<Path> imageFiles() throws IOException {
    return Files.list(imageDir)
      .filter(f -> f.toString().toUpperCase().endsWith(".PCX"))
      .collect(Collectors.toList());
  }

  public BufferedImage loadImage(Path image) throws IOException {
    PcxImageParser parser = new PcxImageParser();
    Map params = new HashMap<>();
    try (InputStream is = Files.newInputStream(image)) {
      ByteSourceInputStream source = new ByteSourceInputStream(is, image.toString());
      return parser.getBufferedImage(source, params);
    }
    catch (ImageReadException ire) {
      throw new IOException(ire);
    }
  }

  public FlashImage nextImage() throws IOException {
    List<Path> paths = imageFiles();
    BufferedImage image = loadImage(paths.get(rnd.nextInt(paths.size())));
    return new FlashImage(image, rnd.nextInt(200));
  }
}
