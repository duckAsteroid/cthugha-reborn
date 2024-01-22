package io.github.duckasteroid.cthugha.audio.io.sim;

import io.github.duckasteroid.cthugha.audio.AudioSample;
import io.github.duckasteroid.cthugha.audio.Channel;
import io.github.duckasteroid.cthugha.audio.dsp.FastFourierTransform;
import io.github.duckasteroid.cthugha.audio.io.AudioSource;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import java.io.IOException;
import java.nio.ShortBuffer;
import java.util.Random;
import javax.sound.sampled.AudioFormat;

public class RandomSimulatedAudio implements AudioSource {
  final Random rnd = new Random();
  final boolean stereo;
  final short stepSize = Short.MAX_VALUE / 10;

  private final FastFourierTransform transform = new FastFourierTransform(512, getFormat(), Channel.MONO_AVG);

  DoubleParameter amplification = new DoubleParameter("amplification", 0, Double.MAX_VALUE, 1.0d);

  public RandomSimulatedAudio(boolean stereo) {
    this.stereo = stereo;
  }

  @Override
  public DoubleParameter getAmplitude() {
    return amplification;
  }

  @Override
  public AudioFormat getFormat() {
    return new AudioFormat(44100f, 16, stereo ? 2 : 1, true, false);
  }

  @Override
  public boolean isMono() {
    return !stereo;
  }

  public AudioSample sample(final int width) {
    // create new random walk sound
    short[] sound = new short[width];
    int height = (int)((Short.MAX_VALUE / 2) * amplification.value);
    for(int i = 0; i < sound.length; i++) {
      sound[i] = nextSample((short) 0, height);
    }
    return new AudioSample(ShortBuffer.wrap(sound), false, 1.0,  transform);
  }

  private short nextSample(short current, int height) {
    short result = (short) (current + rand(-stepSize, stepSize));
    if (result < -height) {
      return (short) -height;
    }
    else if (result > height) {
      return (short) height;
    }
    return result;
  }

  short rand(int low, int high) {
    return (short) ( low + rnd.nextDouble()*(high-low));
  }

  @Override
  public void close() throws IOException {

  }
}
