package io.github.duckasteroid.cthugha.audio.io;

import io.github.duckasteroid.cthugha.audio.AudioSample;
import java.io.IOException;
import java.nio.ShortBuffer;
import java.util.Random;
import javax.sound.sampled.AudioFormat;

public class RandomSimulatedAudio implements AudioSource{
  final Random rnd = new Random();
  final boolean stereo;
  final short stepSize = Short.MAX_VALUE / 10;

  double amplification = 1.0d;

  public RandomSimulatedAudio(boolean stereo) {
    this.stereo = stereo;
  }

  @Override
  public AudioFormat getFormat() {
    return new AudioFormat(44100f, 16, 2, true, false);
  }

  @Override
  public boolean isMono() {
    return false;
  }

  public AudioSample sample(final int width) {
    // create new random walk sound
    short[] sound = new short[width];
    int height = (int)((Short.MAX_VALUE / 2) * amplification);
    for(int i = 0; i < sound.length; i++) {
      sound[i] = nextSample((short) 0, height);
    }
    return new AudioSample(ShortBuffer.wrap(sound), false, 1.0);
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
