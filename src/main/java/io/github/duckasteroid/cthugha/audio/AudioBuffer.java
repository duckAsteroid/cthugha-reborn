package io.github.duckasteroid.cthugha.audio;

import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
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

  private DoubleParameter amplification = new DoubleParameter("Amplification", 0, 100, 1);
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



  public AudioSample readFrom(TargetDataLine line, int length) {
    // how many samples are available?
    int available = line.available() / bytesPerSample;
    bufferDepth.add(available);

    buffer.clear();
    buffer.limit(Math.min(length * bytesPerSample, buffer.capacity()));
    int read = line.read(buffer.array(), 0, Math.min(buffer.limit(), line.available()));
    buffer.position(read);
    buffer.flip();
    ShortBuffer intBuffer = buffer.asShortBuffer();
    return new AudioSample(intBuffer, isMono(), amplification.value);
  }



}
