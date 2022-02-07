package io.github.duckasteroid.cthugha.audio;

import io.github.duckasteroid.cthugha.stats.Stats;
import io.github.duckasteroid.cthugha.stats.StatsFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;
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

    public Stream<short[]> stream() {
      return Arrays.stream(samples);
    }

    public Stream<AudioPoint> streamPoints() {
      return stream().map(AudioPoint::new);
    }

    public Stream<short[]> stream(int maxLength) {
      int skip = samples.length / maxLength;
      return IntStream.range(0, samples.length)
        .filter(n -> n % skip == 0)
        .mapToObj(n -> samples[n]);
    }

    public Stream<AudioPoint> streamPoints(int maxLength) {
      return stream(maxLength).map(AudioPoint::new);
    }
  }

  public enum Channel {
    LEFT {
      @Override
      public short value(short[] sample) {
        return sample[0];
      }
    },
    RIGHT {
      @Override
      public short value(short[] sample) {
        if (sample.length > 1)
          return sample[1];
        else
          return sample[0];
      }
    },
    MONO_AVG {
      @Override
      public short value(short[] sample) {
        double sum = 0;
        for(int i = 0; i < sample.length; i++) {
          sum += sample[i];
        }
        return (short)(sum / sample.length);
      }
    },
    MONO_SUB {
      @Override
      public short value(short[] sample) {
        if (sample.length > 1) {
          return (short) (sample[1] - sample[0]);
        }
        return sample[0];
      }
    };

    public abstract short value(short[] sample);
  }

  public static class AudioPoint {
    final short[] sample;

    public AudioPoint(short[] sample) {
      this.sample = sample;
    }

    public short value(Channel ch) {
      return ch.value(sample);
    }

    public double normalised(Channel ch) {
      return (double) Short.MAX_VALUE / ch.value(sample);
    }

    public int ranged(Channel ch, int min, int max) {
      int range = max - min;
      double extent = normalised(ch) * range;
      return min + (int)extent;
    }
  }
}
