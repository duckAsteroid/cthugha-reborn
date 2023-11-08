package io.github.duckasteroid.cthugha.display;

import io.github.duckasteroid.cthugha.audio.AudioSample;

public interface RenderedItem {
  void render(Display display, AudioSample sample);
}
