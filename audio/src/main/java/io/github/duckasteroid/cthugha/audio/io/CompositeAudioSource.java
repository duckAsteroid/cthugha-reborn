package io.github.duckasteroid.cthugha.audio.io;

import io.github.duckasteroid.cthugha.audio.AudioSample;
import io.github.duckasteroid.cthugha.params.AbstractNode;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioFormat;


record Pair(AudioSource audioSource, String name){
  public static Pair of(AudioSource audioSource, String name) {
    return new Pair(audioSource, name);
  }
}

/**
 * A utility to wrap a bunch of audio sources into a "fake" audio source to permit enumeration
 */
public class CompositeAudioSource extends AbstractNode implements AudioSource {
  private List<Pair> sources = new ArrayList<>();
  private int selected = 0;
  public CompositeAudioSource(AudioSource ... sources) {
    for(AudioSource src : sources) {
      if (src.getSourceNames().isEmpty()) {
        this.sources.add(Pair.of(src, "default"));
      }
      for(String name : src.getSourceNames()) {
        Pair source = Pair.of(src, name);
        this.sources.add(source);
      }
    }
  }

  @Override
  public DoubleParameter getAmplitude() {
    return sources.get(selected).audioSource().getAmplitude();
  }

  @Override
  public AudioSample sample(int width) {
    return sources.get(selected).audioSource().sample(width);
  }

  @Override
  public AudioFormat getFormat() {
    return sources.get(selected).audioSource().getFormat();
  }

  @Override
  public boolean isMono() {
    return sources.get(selected).audioSource().isMono();
  }

  @Override
  public int getSourceIndex() {
    return selected;
  }

  @Override
  public void setSourceIndex(int index) {
    selected = index;
  }

  @Override
  public List<String> getSourceNames() {
    return sources.stream()
      .map(pair -> pair.audioSource().getClass().getSimpleName() + ":"+ pair.name()).toList();
  }

  @Override
  public void close() throws IOException {
    sources.stream().map(Pair::audioSource).forEach(src -> {
      try {
        src.close();
      }
      catch (IOException e) {
        e.printStackTrace();
      }
    });
    sources.clear();
    selected = -1;
  }
}
