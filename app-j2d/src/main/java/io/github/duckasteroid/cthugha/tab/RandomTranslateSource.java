package io.github.duckasteroid.cthugha.tab;

import io.github.duckasteroid.cthugha.display.ScreenBuffer;
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
    sources.add(new Wavy());
  }

  public int[] generate(ScreenBuffer buffer, boolean newSource) {
    if (selected == null || newSource) {
      selected = sources.get(rnd.nextInt(sources.size()));
    }
    selected.randomise();
    return selected.generate(buffer);
  }

  public String getLastGenerated() {
    return selected.toString();
  }
}
