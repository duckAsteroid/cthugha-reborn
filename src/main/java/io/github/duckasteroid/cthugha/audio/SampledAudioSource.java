package io.github.duckasteroid.cthugha.audio;

import io.github.duckasteroid.cthugha.stats.Stats;
import io.github.duckasteroid.cthugha.stats.StatsFactory;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.time.Duration;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

public class SampledAudioSource implements AudioSource {
  private final AudioFormat ideal = new AudioFormat( 44100f, 16, 2, true, false);

  private final TargetDataLine openLine;
  private final AudioBuffer buffer;

  public SampledAudioSource() throws LineUnavailableException {
    this.openLine = getAudioTargetLine(ideal);
    int bufferSize = ideal.getChannels() * (ideal.getSampleSizeInBits() / 8) // size of a sample
      * (int)(0.2/* seconds*/ * ideal.getSampleRate()); // number of samples in X seconds
    this.openLine.open(ideal, bufferSize);
    this.buffer = new AudioBuffer(ideal, Duration.ofMillis(200));

    this.openLine.start();
  }

  @Override
  public AudioFormat getFormat() {
    return buffer.getFormat();
  }

  @Override
  public boolean isMono() {
    return buffer.isMono();
  }

  @Override
  public AudioBuffer.AudioSample sample(int length) {
    return buffer.readFrom(openLine, length);
  }

  @Override
  public void close() throws IOException {
    openLine.close();
  }

  private static Mixer.Info getMixer() {
    return Stream.of(AudioSystem.getMixerInfo())
      .filter(info -> info.getName().startsWith("CABLE Output"))
      .findFirst().orElseThrow(() -> new RuntimeException("No mixer"));
  }

  private static TargetDataLine getAudioTargetLine(AudioFormat format)
    throws LineUnavailableException {
    TargetDataLine line;
    Mixer mixer = AudioSystem.getMixer(getMixer());
    DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
    if (!mixer.isLineSupported(info)) {
      return null;
    }
    line = (TargetDataLine) mixer.getLine(info);
    line.open(format, line.getBufferSize());
    return line;
  }

  public static void main(String[] args) throws LineUnavailableException {
    Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
    for (Mixer.Info info: mixerInfos){
      Mixer m = AudioSystem.getMixer(info);
      Line.Info[] lineInfos = m.getTargetLineInfo();
      if (lineInfos.length > 0) {
        System.out.println("MIXER: "+ info.getName());
      }
      for (Line.Info lineInfo:lineInfos){
        if (lineInfo instanceof DataLine.Info) {
          System.out.println ("\t---"+lineInfo.getLineClass().getSimpleName());
          DataLine.Info dli = (DataLine.Info) lineInfo;
          System.out.println("\t---"+Stream.of(dli.getFormats()).map(fmt -> "{" + fmt.toString() + "}")
            .collect(Collectors.joining(", ")));
        }
      }
    }

  }
}
