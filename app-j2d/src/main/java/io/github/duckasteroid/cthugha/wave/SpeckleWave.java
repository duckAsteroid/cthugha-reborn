package io.github.duckasteroid.cthugha.wave;

import io.github.duckasteroid.cthugha.display.ScreenBuffer;
import io.github.duckasteroid.cthugha.audio.AudioSample;
import io.github.duckasteroid.cthugha.audio.Channel;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import java.util.Random;
import java.util.stream.IntStream;

public class SpeckleWave implements Wave {
  public DoubleParameter fractionMax = new DoubleParameter("Fraction max", .0001, 0.9999, 0.01);
  public DoubleParameter triggerPoint = new DoubleParameter("Trigger point", 0.00001, 0.99999, 0.2);
  Random rnd = new Random();

  public SpeckleWave fraction(double f) {
    this.fractionMax.value = Math.max(1.0, f);
    return this;
  }

  @Override
  public void wave(AudioSample sound, ScreenBuffer buffer) {
    // FIXME use FFT DC (0Hz)
  }
}
