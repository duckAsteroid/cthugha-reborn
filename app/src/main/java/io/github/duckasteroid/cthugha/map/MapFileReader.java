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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

/**
 * Reads the colour palette data from a MAP file
 */
public class MapFileReader {

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
   * Reads every non-blank "R G B" line in the file as one palette entry — any count ≥ 1, not
   * just the historical fixed 256 (the render pipeline sizes its palette LUT texture off
   * {@link PaletteMap#size()}, so it was never actually limited to 256; only this parser was).
   */
  private int[] loadData(Reader reader) throws IOException {
    List<Integer> result = new ArrayList<>();
    try (BufferedReader br = new BufferedReader(reader)) {
      String line;
      int i = 0;
      while ((line = br.readLine()) != null) {
        if (line.isBlank()) continue;
        Matcher matcher = pattern.matcher(line);
        if (!matcher.find()) throw new IllegalArgumentException("Bad line["+i+"]: "+line);
        int r = Integer.parseInt(matcher.group(1));
        int g = Integer.parseInt(matcher.group(2));
        int b = Integer.parseInt(matcher.group(3));
        result.add(new Color(r, g, b).getRGB());
        i++;
      }
    }
    if (result.isEmpty()) throw new IllegalArgumentException("Palette file has no colour entries");
    return result.stream().mapToInt(Integer::intValue).toArray();
  }

  /**
   * Writes {@code colors} (packed {@code 0xRRGGBB} ints) as {@code <name>.MAP} under the palette
   * directory, one "R G B" line per entry, overwriting any existing file of that name and
   * invalidating its cache entry. {@code name} must be a bare filename stem (no path separators
   * or {@code .MAP} extension). Returns the path written.
   */
  public Path write(String name, int[] colors) throws IOException {
    if (name.isBlank() || !name.equals(Paths.get(name).getFileName().toString())) {
      throw new IllegalArgumentException("Invalid palette name: " + name);
    }
    if (colors.length == 0) {
      throw new IllegalArgumentException("Palette must have at least one colour");
    }
    Path file = paletteDir.resolve(name + ".MAP");
    StringBuilder sb = new StringBuilder();
    for (int packed : colors) {
      Color c = new Color(packed);
      sb.append(c.getRed()).append(' ').append(c.getGreen()).append(' ').append(c.getBlue()).append('\n');
    }
    Files.writeString(file, sb.toString());
    cache.remove(file);
    return file;
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
