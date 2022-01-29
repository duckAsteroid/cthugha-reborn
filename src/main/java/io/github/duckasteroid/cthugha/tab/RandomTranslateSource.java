package io.github.duckasteroid.cthugha.tab;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RandomTranslateSource {
  private static Random rnd = new Random();

  private static List<TranslateTableSource> sources = new ArrayList<>();

  private TranslateTableSource selected;

  static {
    sources.add(new Hurricane());
    sources.add(new Smoke());
    sources.add(new Space());
    sources.add(new Spiral());
    sources.add(new BigHalfWheel());
    sources.add(new DownSpiral());
  }

  public int[] generate(Dimension size, boolean newSource) {
    if (selected == null || newSource) {
      selected = sources.get(rnd.nextInt(sources.size()));
    }
    selected.randomiseParameters();
    return selected.generate(size);
  }

  public String getLastGenerated() {
    return selected.toString();
  }
}
