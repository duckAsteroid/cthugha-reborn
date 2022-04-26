package io.github.duckasteroid.cthugha.wave;

import io.github.duckasteroid.cthugha.ScreenBuffer;
import io.github.duckasteroid.cthugha.audio.AudioSample;

/**
 * Interface to an object that renders the audio wave on the screen
 */
public interface Wave {
  void wave(AudioSample sound, ScreenBuffer buffer);
}
