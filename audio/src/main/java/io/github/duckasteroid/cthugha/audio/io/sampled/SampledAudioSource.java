package io.github.duckasteroid.cthugha.audio.io.sampled;

import io.github.duckasteroid.cthugha.audio.AudioSample;
import io.github.duckasteroid.cthugha.audio.Channel;
import io.github.duckasteroid.cthugha.audio.dsp.FastFourierTransform;
import io.github.duckasteroid.cthugha.audio.io.AudioSource;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.stats.Stats;
import io.github.duckasteroid.cthugha.stats.StatsFactory;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SampledAudioSource implements AudioSource {
  private static final Logger LOG = LoggerFactory.getLogger(SampledAudioSource.class);
  public final static AudioFormat IDEAL = new AudioFormat( 44100f, 16, 2, true, true);
  public final static int
    BUFFER_SIZE = IDEAL.getChannels() * (IDEAL.getSampleSizeInBits() / 8) // size of a sample
    * (int) (0.2/* seconds*/ * IDEAL.getSampleRate()); // number of samples in X seconds
  private TargetDataLine openLine;
  private final ByteBuffer buffer;
  private final AudioFormat format;
  private final int bytesPerSample;
  private final List<LineAcquirer.MixerLine> mixerLines;

  private int lineIndex = 0;
  private DoubleParameter amplification = new DoubleParameter("Amplification", 0, 100, 1);

  private final FastFourierTransform fastFourierTransform;

  /**
   * Tracks statistics on the actual depth of the audio buffer on read
   * (how much audio data was waiting)
   */
  private final Stats bufferDepth = StatsFactory.stats("audio.buffer.depthOnRead");

  public SampledAudioSource() throws LineUnavailableException {
    LineAcquirer laq = new LineAcquirer();
    mixerLines = laq.allLinesMatching(TargetDataLine.class, IDEAL);
    this.format = IDEAL;
    this.bytesPerSample = format.getChannels() * (format.getSampleSizeInBits() / 8);
    double seconds = 200 / 1000.0d; // 200 ms
    int numSamples =  (int)Math.round(seconds * format.getSampleRate());
    this.buffer = ByteBuffer.allocate(bytesPerSample * numSamples);
    this.buffer.order(format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
    setSourceIndex(0);

    fastFourierTransform = new FastFourierTransform(512, IDEAL, Channel.MONO_AVG);
  }

  @Override
  public DoubleParameter getAmplitude() {
    return amplification;
  }

  @Override
  public List<String> getSourceNames() {
    return mixerLines.stream().map(LineAcquirer.MixerLine::toString).toList();
  }

  @Override
  public int getSourceIndex() {
    return lineIndex;
  }

  @Override
  public void setSourceIndex(int index) {
    this.lineIndex = index;
    try {
      if (this.openLine != null) {
        this.openLine.close();
      }
      this.openLine = mixerLines.get(index).getTargetDataLine();
      this.openLine.open(IDEAL, BUFFER_SIZE);
      this.openLine.start();
    }
    catch (LineUnavailableException e) {
      LOG.error("Unable to open line", e);
    }
  }

  @Override
  public AudioFormat getFormat() {
    return format;
  }

  @Override
  public boolean isMono() {
    return format.getChannels() == 1;
  }

  @Override
  public AudioSample sample(int length) {
      // how many samples are available?
      int available = openLine.available() / bytesPerSample;
      bufferDepth.add(available);

      buffer.clear();
      buffer.limit(Math.min(length * bytesPerSample, buffer.capacity()));
      int read = openLine.read(buffer.array(), 0, Math.min(buffer.limit(), openLine.available()));
      buffer.position(read);
      buffer.flip();
      ShortBuffer intBuffer = buffer.asShortBuffer();
      return new AudioSample(intBuffer, isMono(), amplification.value, fastFourierTransform);

  }

  @Override
  public void close() throws IOException {
    openLine.close();
  }

  private static Mixer.Info getMixer() {
    final String preferred = System.getProperty("cthugha.mixer", "Speakers/Headphones");
    return Stream.of(AudioSystem.getMixerInfo())
      .filter(info -> info.getName().startsWith(preferred)) // "Microphone (Realtek(R) Audio)"
      .findFirst().orElseThrow(() -> new RuntimeException("No mixer '"+preferred+"'"));
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
      List<Line.Info> lineInfos =
        Stream.concat(Arrays.stream(m.getTargetLineInfo()), Arrays.stream(m.getSourceLineInfo())).toList();
      if (lineInfos.size() > 0) {
        System.out.println("MIXER: "+ info.getName());
      }
      for (Line.Info lineInfo: lineInfos){
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
