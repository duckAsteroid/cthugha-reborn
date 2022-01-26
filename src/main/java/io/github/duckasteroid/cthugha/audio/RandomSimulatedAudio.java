package io.github.duckasteroid.cthugha.audio;

import java.io.IOException;
import java.util.Random;

public class RandomSimulatedAudio implements AudioSource{
  final Random rnd = new Random();

  public void sample(final int[] sound, final int width, final int height) {
    // create new random walk sound
    sound[0] = rand(-height/3,height/3);
    for(int i = 1; i < width; i++) {
      sound[i] = sound[i-1] + rand(-10,10);
      if(sound[i] < -height) {
        sound[i] += 2 * height;
      }
      if(sound[i] > height){
        sound[i] -= 2 * height;
      }
    }
  }

  int rand(int low, int high) {
    return (int)( low + rnd.nextDouble()*(high-low));
  }

  @Override
  public void close() throws IOException {

  }
}
