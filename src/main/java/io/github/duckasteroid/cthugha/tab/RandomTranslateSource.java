package io.github.duckasteroid.cthugha.tab;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RandomTranslateSource {
  private static final List<TranslateTableSource> sources = new ArrayList<>();

  private TranslateTableSource selected;

  static {
    sources.add(new BigHalfWheel());
    sources.add(new DownSpiral());
    sources.add(new Hurricane());
    sources.add(new LinearSlider());
    sources.add(new Smoke());
    sources.add(new Space());
    sources.add(new Spiral());
    sources.add(new SpiralGalaxyPlughole());
  }

  public int[] generate(int width, int height, boolean newSource, Random rng) {
    if (selected == null || newSource) {
      selected = sources.get(rng.nextInt(sources.size()));
    }
    selected.randomise(rng);
    return selected.generate(width, height);
  }

  public String getLastGenerated() {
    return selected.toString();
  }
}
