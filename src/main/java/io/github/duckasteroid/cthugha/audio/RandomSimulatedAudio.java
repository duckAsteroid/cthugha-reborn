package io.github.duckasteroid.cthugha.audio;

import java.io.IOException;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.sound.sampled.AudioFormat;

public class RandomSimulatedAudio implements AudioSource{
  final Random rnd = new Random();
  final boolean stereo;

  public RandomSimulatedAudio(boolean stereo) {
    this.stereo = stereo;
  }

  @Override
  public AudioFormat getFormat() {
    return new AudioFormat(44100f, 16, 1, true, false);
  }

  @Override
  public boolean isMono() {
    return true;
  }

  public AudioBuffer.AudioSample sample(final int width) {
    // create new random walk sound
    short[][] sound = new short[width][1];
    int height = Short.MAX_VALUE;
    sound[0] = new short[]{ rand(-height/3,height/3)};
    for(int i = 1; i < width; i++) {
      sound[i] = new short[] {(short) (sound[i-1][0] + rand(-10,10))};
      if(sound[i][0] < -height) {
        sound[i][0] += 2 * height;
      }
      if(sound[i][0] > height){
        sound[i][0] -= 2 * height;
      }
    }
    return new AudioBuffer.AudioSample(sound, true);
  }

  short rand(int low, int high) {
    return (short) ( low + rnd.nextDouble()*(high-low));
  }

  @Override
  public void close() throws IOException {

  }
}
