package io.github.duckasteroid.cthugha.wave;

import io.github.duckasteroid.cthugha.ScreenBuffer;

/**
 * Interface to an object that renders the audio wave on the screen
 */
public interface Wave {
  void wave(int[] sound, ScreenBuffer buffer);
}
