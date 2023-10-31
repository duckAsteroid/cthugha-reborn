package io.github.duckasteroid.cthugha.wave;

import io.github.duckasteroid.cthugha.ScreenBuffer;
import io.github.duckasteroid.cthugha.audio.AudioSample;
import io.github.duckasteroid.cthugha.audio.Channel;
import io.github.duckasteroid.cthugha.params.DoubleParameter;
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
    final int max = (int)(buffer.pixels.length * fractionMax.value);
    double intensity = sound.intensity(Channel.MONO_AVG); // FIXME use FFT DC (0Hz)
    double relativeIntensity =  intensity / Short.MAX_VALUE;
    if (relativeIntensity > triggerPoint.value) {
      IntStream.range(0, (int) (max * relativeIntensity))
        .forEach(i -> {
          int x = rnd.nextInt(buffer.width);
          int y = rnd.nextInt(buffer.height);
          buffer.pixels[buffer.index(x, y)] = (byte) 255;
        });
    }
  }
}
