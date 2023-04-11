package io.github.duckasteroid.cthugha.audio;

import io.github.duckasteroid.cthugha.stats.Stats;
import io.github.duckasteroid.cthugha.stats.StatsFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.time.Duration;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.TargetDataLine;

/**
 * This class holds our in memory buffer for audio data.
 * It provides facilities to read/stream in more useful numeric forms.
 */
public class AudioBuffer {
  private final AudioFormat format;
  private final int bytesPerSample;
  private final ByteBuffer buffer;

  private double amplification = 1.0d;
  /**
   * Tracks statistics on the actual depth of the audio buffer on read
   * (how much audio data was waiting)
   */
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

  public double getAmplification() {
    return amplification;
  }

  public void setAmplification(double amplification) {
    this.amplification = amplification;
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
        short ch1 = (short)(intBuffer.get() * amplification);
        sample[index][0] = ch1;
        if (sampleSize > 1) {
          short ch2 = (short)(intBuffer.get() * amplification);
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

}
