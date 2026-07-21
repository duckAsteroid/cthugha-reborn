package io.github.duckasteroid.cthugha.display;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;

public class HtmlColors {
  private static final HtmlColors SINGLETON = new HtmlColors();
  private final Map<String, Color> values = new HashMap<>();

  private HtmlColors() {
    try (Stream<String> lines = new BufferedReader(new InputStreamReader(
      Objects.requireNonNull(HtmlColors.class.getResourceAsStream("/colors")))).lines()) {
      lines.filter(line -> line.contains("\t")) // only those with tabs
        .map(line -> line.split("\t"))
        .map(cols -> Pair.of(cols[0], Color.decode("0x"+cols[1])))
        .forEach(pair -> values.put(pair.getKey(), pair.getValue()));
    }
  }

  public static Set<String> names() {
    return SINGLETON.values.keySet();
  }

  public static Color get(String name) {
    return SINGLETON.values.get(name);
  }
}
