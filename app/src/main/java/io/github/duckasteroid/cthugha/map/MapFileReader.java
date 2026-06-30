package io.github.duckasteroid.cthugha.map;

import java.awt.Color;
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

/**
 * Reads the colour palette data from a MAP file
 */
public class MapFileReader {
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

  public PaletteMap load(final Path path) throws IOException {
    if(!cache.containsKey(path)) {
      try(Reader reader = Files.newBufferedReader(path)) {
        int[] colors = loadData(reader);
        cache.put(path, new PaletteMap(path.toString(), colors));
      }
    }
    return cache.get(path);
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

  public static void main(String[] args) throws IOException {
    String path = args.length == 0 ? "maps" : args[0];
    Path dir = Paths.get(path);
    System.out.println("Processing "+dir.toAbsolutePath());
    MapFileReader reader = new MapFileReader(dir);
    for (Path f : reader.paletteFiles()) {
      PaletteMap map = reader.load(f);
      ImageIO.write(map.getPaletteImage(32), "PNG", dir.resolve(f.getFileName() +".png").toFile());
    }

  }
}
