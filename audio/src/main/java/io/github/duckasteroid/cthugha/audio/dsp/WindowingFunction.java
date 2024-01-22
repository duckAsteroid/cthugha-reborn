package io.github.duckasteroid.cthugha.audio.dsp;

import static java.lang.Math.PI;
import static java.lang.Math.cos;

import io.github.duckasteroid.cthugha.audio.AudioValue;

public abstract class WindowingFunction {

  public static class Cosine extends WindowingFunction {
    private final int size;

    public Cosine(int size) {
      this.size = size;
    }

    @Override
    public double apply(AudioValue v) {
      return v.value() * (1 - cos((2*PI*v.index()) / size));
    }
  }

  public abstract double apply(AudioValue v);

}
