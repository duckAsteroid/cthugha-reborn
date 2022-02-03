package io.github.duckasteroid.cthugha.wave;

import io.github.duckasteroid.cthugha.ScreenBuffer;
import io.github.duckasteroid.cthugha.audio.AudioBuffer;
import java.util.stream.Stream;

/**
 * Interface to an object that renders the audio wave on the screen
 */
public interface Wave {
  void wave(AudioBuffer.AudioSample sound, ScreenBuffer buffer);
}
