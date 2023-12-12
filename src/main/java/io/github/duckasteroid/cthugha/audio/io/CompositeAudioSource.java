package io.github.duckasteroid.cthugha.audio.io;

import io.github.duckasteroid.cthugha.audio.AudioSample;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import org.apache.commons.lang3.tuple.Pair;

public class CompositeAudioSource implements AudioSource {
  private List<Pair<AudioSource, String>> sources = new ArrayList<>();
  private int selected = 0;
  public CompositeAudioSource(AudioSource ... sources) {
    for(AudioSource src : sources) {
      if (src.getSourceNames().isEmpty()) {
        this.sources.add(Pair.of(src, "default"));
      }
      for(String name : src.getSourceNames()) {
        Pair<AudioSource, String> source = Pair.of(src, name);
        this.sources.add(source);
      }
    }
  }

  @Override
  public AudioSample sample(int width) {
    return sources.get(selected).getKey().sample(width);
  }

  @Override
  public AudioFormat getFormat() {
    return sources.get(selected).getKey().getFormat();
  }

  @Override
  public boolean isMono() {
    return sources.get(selected).getKey().isMono();
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
      .map(pair -> pair.getLeft().getClass().getSimpleName() + ":"+ pair.getRight()).toList();
  }

  @Override
  public void close() throws IOException {
    sources.stream().map(Pair::getLeft).forEach(src -> {
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
