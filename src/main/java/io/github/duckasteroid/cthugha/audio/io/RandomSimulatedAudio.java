package io.github.duckasteroid.cthugha.audio.io;

import io.github.duckasteroid.cthugha.audio.AudioSample;
import java.io.IOException;
import java.util.Random;
import javax.sound.sampled.AudioFormat;

public class RandomSimulatedAudio implements AudioSource{
  final Random rnd = new Random();
  final boolean stereo;
  final short stepSize = Short.MAX_VALUE / 10;

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
    short[][] sound = new short[width][1];
    int height = Short.MAX_VALUE;
    sound[0] = new short[]{ nextSample((short) 0, height), nextSample((short) 0, height)};
    for(int i = 1; i < width; i++) {
      sound[i] = new short[] {nextSample(sound[i-1][0], height), nextSample(sound[i-1][1], height)};
    }
    return new AudioSample(sound, false);
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
