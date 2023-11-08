package io.github.duckasteroid.cthugha.audio.io;

import io.github.duckasteroid.cthugha.audio.AudioSample;
import io.github.duckasteroid.cthugha.params.AbstractNode;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import java.io.IOException;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import javax.sound.sampled.AudioFormat;

public class SimulatedFrequenciesAudioSource implements AudioSource {
  private final AudioFormat format = new AudioFormat(44100f, 16, 2, true, false);
  private long counter = 0;

  public DoubleParameter amplitude = new DoubleParameter("Amplitude", 0, 100, 1);

  public final List<Oscillator> oscillators;

  private static final short waveHeight =  Short.MAX_VALUE / 8;

  public SimulatedFrequenciesAudioSource(int oscillatorCount) {
    oscillators = IntStream.range(1, oscillatorCount + 1)
      .mapToObj(i -> new Oscillator("Oscillator "+ i, i * 261.63))
      .toList();
  };

  public static class Oscillator extends AbstractNode {
    public DoubleParameter frequency = new DoubleParameter("Frequency (Hz)", 20, 20000);

    public Oscillator(String name, double hz) {
      super(name);
      initChildren(frequency);
      frequency.setValue(hz);
    }
  }

  @Override
  public double getAmplitude() {
    return amplitude.value;
  }

  @Override
  public void setAmplitude(double amplitude) {
    this.amplitude.value = amplitude;
  }

  @Override
  public AudioSample sample(int width) {
    ShortBuffer buffer = ShortBuffer.wrap(new short[width * format.getChannels()]);
    while(buffer.hasRemaining()) {
      double value = oscillators.stream().mapToDouble(osc -> osc.frequency.value)
        .map(freq -> Math.sin(2 * Math.PI *  counter * (freq / format.getSampleRate())) * waveHeight)
        .sum() / oscillators.size();
      value *= amplitude.value;
      counter++;
      for(int ch = 0 ; ch < format.getChannels(); ch++) {
        buffer.put((short) value);
        if (!buffer.hasRemaining()) {
          break;
        }
      }
    }
    buffer.flip();
    return new AudioSample(buffer, false, 1.0);
  }

  @Override
  public AudioFormat getFormat() {
    return null;
  }

  @Override
  public boolean isMono() {
    return false;
  }


  @Override
  public void close() throws IOException {

  }
}
