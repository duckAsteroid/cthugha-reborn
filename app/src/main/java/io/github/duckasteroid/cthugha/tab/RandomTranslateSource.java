package io.github.duckasteroid.cthugha.tab;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RandomTranslateSource {
  private static final List<TranslateTableSource> sources = new ArrayList<>();

  private TranslateTableSource selected;
  private int selectedIndex = -1;

  static {
    sources.add(new BigHalfWheel());
    sources.add(new CircleInversion());
    sources.add(new ConcentricRings());
    sources.add(new DownSpiral());
    sources.add(new FisheyeLens());
    sources.add(new GravityWells());
    sources.add(new Hurricane());
    sources.add(new Kaleidoscope());
    sources.add(new LinearSlider());
    sources.add(new LogPolarZoom());
    sources.add(new Pinwheel());
    sources.add(new PlasmaFlow());
    sources.add(new Ripple());
    sources.add(new Shatter());
    sources.add(new SineGridWarp());
    sources.add(new Smoke());
    sources.add(new Space());
    sources.add(new Spiral());
    sources.add(new SpiralGalaxyPlughole());
    sources.add(new Twist());
    sources.add(new WaveInterference());
  }

  public PixelMapper generate(int width, int height, boolean newSource, Random rng) {
    if (selected == null || newSource) {
      selectedIndex = rng.nextInt(sources.size());
      selected = sources.get(selectedIndex);
    }
    selected.randomise(rng);
    return selected.generate(width, height, rng);
  }

  public PixelMapper step(int delta, int width, int height, Random rng) {
    if (selectedIndex < 0) selectedIndex = 0;
    selectedIndex = Math.floorMod(selectedIndex + delta, sources.size());
    selected = sources.get(selectedIndex);
    selected.randomise(rng);
    return selected.generate(width, height, rng);
  }

  public String getLastGenerated() {
    return selected != null ? selected.toString() : "";
  }
}
