package io.github.duckasteroid.cthugha.audio.io.sim;

import io.github.duckasteroid.cthugha.audio.AudioSample;
import io.github.duckasteroid.cthugha.audio.Channel;
import io.github.duckasteroid.cthugha.audio.dsp.FastFourierTransform;
import io.github.duckasteroid.cthugha.audio.io.AudioSource;
import io.github.duckasteroid.cthugha.params.AbstractNode;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import java.io.IOException;
import java.nio.ShortBuffer;
import java.util.List;
import java.util.stream.IntStream;
import javax.sound.sampled.AudioFormat;

public class SimulatedFrequenciesAudioSource extends AbstractNode implements AudioSource {
  private final AudioFormat format = new AudioFormat(44100f, 16, 2, true, true);
  private long counter = 0;

  private final FastFourierTransform transform = new FastFourierTransform(512, format, Channel.MONO_AVG);
  public DoubleParameter amplitude = new DoubleParameter("Amplitude", 0, 100, 1);

  public final List<Oscillator> oscillators;

  private static final short waveHeight =  Short.MAX_VALUE / 8;

  public SimulatedFrequenciesAudioSource(int oscillatorCount) {
    oscillators = IntStream.range(1, oscillatorCount + 1)
      .mapToObj(i -> new Oscillator("Oscillator " + i, i * 261.63))
      .toList();
  }

  @Override
  public DoubleParameter getAmplitude() {
    return amplitude;
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
    return new AudioSample(buffer, false, 1.0, transform);
  }

  @Override
  public AudioFormat getFormat() {
    return format;
  }

  @Override
  public boolean isMono() {
    return false;
  }


  @Override
  public void close() throws IOException {
  }
}
