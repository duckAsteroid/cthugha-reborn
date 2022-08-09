package io.github.duckasteroid.cthugha.audio.dsp;

import java.util.stream.DoubleStream;

public enum WindowingFunction {
  TUKEY_5 {
    @Override
    public void apply(double[] data) {
      for (int i = 0; i < data.length; i++) {

      }
    }
  };

  public abstract void apply(double[] data);
}
