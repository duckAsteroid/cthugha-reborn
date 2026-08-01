package io.github.duckasteroid.cthugha.map;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the colour palette data from a MAP file
 */
public class MapFileReader {
  private static final Logger LOG = LoggerFactory.getLogger(MapFileReader.class);

  /** Height (px) of a generated palette preview PNG; width is always 256 (one column per entry). */
  public static final int PREVIEW_HEIGHT = 32;

  private final Path paletteDir;
  private final Random random = new Random();
  private final Map<Path, PaletteMap> cache = new HashMap<>();

  private final Pattern pattern = Pattern.compile("\\s*(\\d+)\\s*(\\d+)\\s*(\\d+)");

  public MapFileReader(Path paletteDir) {
    this.paletteDir = paletteDir;
  }

  public List<Path> paletteFiles() throws IOException {
    return Files.list(paletteDir)
      .filter(f -> f.toString().toUpperCase().endsWith(".MAP"))
      .collect(Collectors.toList());
  }

  public Path randomPalette() throws IOException {
    List<Path> paths = paletteFiles();
    return paths.get(random.nextInt(paths.size()));
  }

  public PaletteMap random() throws IOException {
    return load(randomPalette());
  }

  /**
   * Deterministic default palette: the first file in sorted (filename) order. Used as the
   * fresh-session default instead of {@link #random()} so a first-ever launch (no persisted
   * "current" state yet — see issue #3) doesn't pick a different-looking palette every time.
   */
  public PaletteMap first() throws IOException {
    List<Path> paths = paletteFiles().stream().sorted().collect(Collectors.toList());
    if (paths.isEmpty()) {
      throw new IOException("No .MAP palette files found in " + paletteDir);
    }
    return load(paths.get(0));
  }

  public PaletteMap load(final Path path) throws IOException {
    if(!cache.containsKey(path)) {
      try(Reader reader = Files.newBufferedReader(path)) {
        int[] colors = loadData(reader);
        cache.put(path, new PaletteMap(path.toString(), colors));
      }
    }
    return cache.get(path);
  }

  /**
   * Renders and writes a preview PNG for {@code mapFile} to {@code <mapFile>.png} in the same
   * directory, overwriting any existing file. Returns the path written.
   */
  public Path writePreview(Path mapFile) throws IOException {
    Path pngFile = mapFile.resolveSibling(mapFile.getFileName() + ".png");
    PaletteMap map = load(mapFile);
    ImageIO.write(map.getPaletteImage(PREVIEW_HEIGHT), "PNG", pngFile.toFile());
    return pngFile;
  }

  /**
   * Checks whether {@code <mapFile>.png} exists and its pixels exactly match what
   * {@link #writePreview(Path)} would currently render for {@code mapFile} — i.e. that the
   * preview isn't missing or stale relative to the (possibly since-edited) source data.
   */
  public boolean previewMatches(Path mapFile) throws IOException {
    Path pngFile = mapFile.resolveSibling(mapFile.getFileName() + ".png");
    if (!Files.exists(pngFile)) {
      return false;
    }
    BufferedImage expected = load(mapFile).getPaletteImage(PREVIEW_HEIGHT);
    BufferedImage actual = ImageIO.read(pngFile.toFile());
    if (actual == null || actual.getWidth() != expected.getWidth() || actual.getHeight() != expected.getHeight()) {
      return false;
    }
    for (int y = 0; y < expected.getHeight(); y++) {
      for (int x = 0; x < expected.getWidth(); x++) {
        if (actual.getRGB(x, y) != expected.getRGB(x, y)) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Regenerates any preview PNG that is missing or stale relative to its {@code .MAP} source,
   * leaving already-accurate previews (and their mtimes, which back the remote UI's HTTP cache
   * headers) untouched. Preview PNGs aren't committed to git — this is what makes them exist,
   * called once at startup so the whole library is ready before the remote UI is served. A
   * single unreadable/malformed {@code .MAP} file is logged and skipped rather than aborting
   * the rest of the library.
   */
  public void refreshPreviews() throws IOException {
    for (Path f : paletteFiles()) {
      try {
        if (!previewMatches(f)) {
          writePreview(f);
        }
      } catch (IOException | RuntimeException e) {
        LOG.warn("Failed to refresh preview for {}", f, e);
      }
    }
  }

  private int[] loadData(Reader reader) throws IOException {
    int[] result = new int[256];
    try (BufferedReader br = new BufferedReader(reader)) {
      for(int i = 0; i < result.length; i++) {
        final String line = br.readLine();
        if (line == null) throw new IllegalArgumentException("Input not long enough");
        Matcher matcher = pattern.matcher(line);
        if (!matcher.find()) throw new IllegalArgumentException("Bad line["+i+"]: "+line);
        int r = Integer.parseInt(matcher.group(1));
        int g = Integer.parseInt(matcher.group(2));
        int b = Integer.parseInt(matcher.group(3));
        Color color = new Color(r,g,b);
        result[i] = color.getRGB();
      }
    }
    return result;
  }

  /**
   * Usage: {@code MapFileReader [--check] [dir]} (dir defaults to {@code maps}).
   * Without {@code --check}, (re)generates every preview PNG. With it, only reports which
   * previews are missing or stale (pixel-mismatched) against their {@code .MAP} source, writes
   * nothing, and exits non-zero if any are found — suitable for a CI drift check.
   */
  public static void main(String[] args) throws IOException {
    boolean check = args.length > 0 && args[0].equals("--check");
    String path = args.length > (check ? 1 : 0) ? args[check ? 1 : 0] : "maps";
    Path dir = Paths.get(path);
    System.out.println("Processing "+dir.toAbsolutePath());
    MapFileReader reader = new MapFileReader(dir);
    boolean anyStale = false;
    for (Path f : reader.paletteFiles()) {
      if (check) {
        boolean ok = reader.previewMatches(f);
        System.out.println((ok ? "OK    " : "STALE ") + f.getFileName());
        anyStale |= !ok;
      } else {
        reader.writePreview(f);
      }
    }
    if (check && anyStale) {
      System.exit(1);
    }
  }
}
