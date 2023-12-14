package io.github.duckasteroid.cthugha.audio.io.sim;

import io.github.duckasteroid.cthugha.params.AbstractNode;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;

public class Oscillator extends AbstractNode {
  public DoubleParameter frequency = new DoubleParameter("Frequency (Hz)", 10, 20000);

  public Oscillator(String name, double hz) {
    super(name);
    initChildren(frequency);
    frequency.setValue(hz);
  }
}
