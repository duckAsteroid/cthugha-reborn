package io.github.duckasteroid.cthugha.audio;

import io.github.duckasteroid.cthugha.stats.Stats;
import io.github.duckasteroid.cthugha.stats.StatsFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.TargetDataLine;

public class AudioBuffer {
  private final AudioFormat format;
  private final int bytesPerSample;
  private final ByteBuffer buffer;

  private final Stats bufferDepth = StatsFactory.stats("audio.buffer.depthOnRead");

  public AudioBuffer(AudioFormat format, Duration duration) {
    this.format = format;
    this.bytesPerSample = format.getChannels() * (format.getSampleSizeInBits() / 8);
    float seconds = duration.toMillis() / 1000.0f;
    int numSamples =  (int)(seconds * format.getSampleRate());
    this.buffer = ByteBuffer.allocate(bytesPerSample * numSamples);
    this.buffer.order(format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
  }

  public AudioFormat getFormat() {
    return format;
  }

  public boolean isMono() {
    return format.getChannels() == 1;
  }

  public AudioSample readFrom(TargetDataLine line, int length) {
    int available = line.available() / bytesPerSample;
    bufferDepth.add(available);
    short[][] sample;
    if (available >= length) {
      buffer.clear();
      int read = line.read(buffer.array(), 0, Math.min(buffer.limit(), line.available()));
      buffer.position(read);
      buffer.flip();
      ShortBuffer intBuffer = buffer.asShortBuffer();
      final int sampleSize = isMono() ? 1 : 2;
      sample = new short[intBuffer.remaining() / sampleSize][];
      int index = 0;
      while(intBuffer.hasRemaining()) {
        sample[index] = new short[sampleSize];
        short ch1 = intBuffer.get();
        sample[index][0] = ch1;
        if (sampleSize > 1) {
          short ch2 = intBuffer.get();
          sample[index][1] = ch2;
        }
        index++;
      }
    }
    else {
      sample = new short[0][0];
    }
    return new AudioSample(sample, isMono());
  }

  public static float normalise(short value) {
    return  (float)value / (float)Short.MAX_VALUE;
  }

  public static int transpose(short value, int fsd) {
    return (int)(normalise(value) * fsd);
  }

  public static class AudioSample {
    public final short[][] samples;
    public final boolean mono;

    public AudioSample(short[][] samples, boolean mono) {
      this.samples = samples;
      this.mono = mono;
    }
  }
}
