package io.github.duckasteroid.cthugha.tab;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RandomTranslateSource {
  private static Random rnd = new Random();

  private static List<TranslateTableSource> sources = new ArrayList<>();

  private TranslateTableSource selected;

  static {
    sources.add(new BigHalfWheel());
    sources.add(new DownSpiral());
    sources.add(new Hurricane());
    sources.add(new LinearSlider());
    sources.add(new Smoke());
    sources.add(new Space());
    sources.add(new Spiral());
  }

  public int[] generate(int width, int height, boolean newSource) {
    if (selected == null || newSource) {
      selected = sources.get(rnd.nextInt(sources.size()));
    }
    selected.randomise();
    return selected.generate(width, height);
  }

  public String getLastGenerated() {
    return selected.toString();
  }
}
