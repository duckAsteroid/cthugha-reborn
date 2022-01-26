package io.github.duckasteroid.cthugha.map;

import java.awt.image.IndexColorModel;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reads the colour palette data from a MAP file
 */
public class MapFileReader {
  private final Path paletteDir;
  private final Random random = new Random();

  private final byte[] reds = new byte[256];
  private final byte[] greens = new byte[256];
  private final byte[] blues = new byte[256];

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

  public IndexColorModel random() throws IOException {
    return load(randomPalette());
  }

  public IndexColorModel load(Path path) throws IOException {
    return load(Files.newBufferedReader(path));
  }

  private IndexColorModel load(Reader reader) throws IOException {
    try (BufferedReader br = new BufferedReader(reader)) {
      for(int i = 0; i < 256; i++) {
        final String line = br.readLine();
        if (line == null) throw new IllegalArgumentException("Input not long enough");
        Matcher matcher = pattern.matcher(line);
        if (!matcher.find()) throw new IllegalArgumentException("Bad line["+i+"]: "+line);
        reds[i] = (byte) (Integer.parseInt(matcher.group(1)) & 0xFF);
        greens[i] = (byte) (Integer.parseInt(matcher.group(2)) & 0xFF);
        blues[i] = (byte) (Integer.parseInt(matcher.group(3)) & 0xFF);
      }
    }
    return new IndexColorModel(8, 256, reds, greens, blues);
  }
}
