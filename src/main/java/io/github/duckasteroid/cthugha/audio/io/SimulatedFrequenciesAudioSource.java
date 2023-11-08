package io.github.duckasteroid.cthugha.audio.io;

import io.github.duckasteroid.cthugha.audio.AudioSample;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import java.io.IOException;
import java.nio.ShortBuffer;
import java.util.stream.DoubleStream;
import javax.sound.sampled.AudioFormat;

public class SimulatedFrequenciesAudioSource implements AudioSource {
  private final AudioFormat format = new AudioFormat(44100f, 16, 2, true, false);
  private long counter = 0;

  public DoubleParameter amplitude = new DoubleParameter("Amplitude", 0, 100, 1);

  private static final short waveHeight =  Short.MAX_VALUE / 8;

  private static final double[] frequencies = new double[] {
    5,
    261.63,
    261.63 * 3
  };

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
      double value = DoubleStream.of(frequencies)
        .map(freq -> Math.sin(2 * Math.PI *  counter * (freq / format.getSampleRate())) * waveHeight)
        .sum() / frequencies.length;
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
