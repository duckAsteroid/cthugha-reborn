package com.asteroid.duck.opengl.util.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Discovers real Java Sound {@link javax.sound.sampled.TargetDataLine}s for live audio capture.
 *
 * <p>Purely a discovery utility — it does not hold any selection or opened state. Turn a
 * discovered {@link MixerLine} into an {@link AudioDataSource} via
 * {@link MixerLine#toAudioDataSource()} and add it to an {@link AudioSources} alongside any
 * other sources (e.g. from {@code com.asteroid.duck.opengl.util.audio.simulated.SimulatedSources}).</p>
 *
 * <pre>{@code
 * AudioSources sources = new AudioSources();
 * LineAcquirer.allLinesMatching(LineAcquirer.IDEAL)
 *         .map(LineAcquirer.MixerLine::toAudioDataSource)
 *         .forEach(sources::add);
 * }</pre>
 */
public final class LineAcquirer {
  private static final Logger LOG = LoggerFactory.getLogger(LineAcquirer.class);

  private LineAcquirer() {}

  /**
   * A pairing of a Java Sound {@link Mixer} with one of its available {@link DataLine.Info} descriptors.
   * Represents a candidate audio capture line before it has been opened.
   *
   * @param mixer the mixer that owns this line
   * @param line  descriptor for the specific line on the mixer
   */
  public record MixerLine(Mixer mixer, DataLine.Info line) {
    /**
     * Wrap this candidate as an {@link AudioDataSource}. The underlying line is not acquired or
     * opened until {@link AudioDataSource#open} is called.
     *
     * @return a lazily-opened source
     */
    public AudioDataSource toAudioDataSource() {
      return new TargetLineSource(displayName(), mixer, line);
    }

    /**
     * A short, UI-friendly name for this line — just the mixer name.
     *
     * @return concise display name
     */
    public String displayName() {
      return mixer.getMixerInfo().getName();
    }

    @Override
    public String toString() {
      return mixer.getMixerInfo().getName() + ":"+ line.toString();
    }
  }

  /**
   * Enumerate all data lines across all installed Java Sound mixers.
   *
   * @return a list of every available {@link MixerLine} regardless of format support
   */
  public static List<MixerLine> allLines() {
    ArrayList<MixerLine> result = new ArrayList<>();
    Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
    for (Mixer.Info info: mixerInfos){
      Mixer m = AudioSystem.getMixer(info);
      List<Line.Info> lineInfos =
        Stream.concat(Arrays.stream(m.getTargetLineInfo()), Arrays.stream(m.getSourceLineInfo())).toList();
      for (Line.Info lineInfo: lineInfos){
        if (lineInfo instanceof DataLine.Info dli) {
          result.add(new MixerLine(m, dli));
        }
      }
    }
    return result;
  }

  /**
   * Filter all available lines to those that support the given audio format as a capture target.
   *
   * @param format the required audio format (e.g. {@link #IDEAL})
   * @return a stream of matching {@link MixerLine}s
   */
  public static Stream<MixerLine> allLinesMatching(AudioFormat format) {
    DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
    return allLinesMatching(info);
  }
  /**
   * Filter all available lines to those whose class and mixer support the given {@link DataLine.Info}.
   *
   * @param info the line-info descriptor specifying class and format requirements
   * @return a stream of {@link MixerLine}s that can satisfy {@code info}
   */
  public static Stream<MixerLine> allLinesMatching(DataLine.Info info) {
    return allLines().stream()
            .filter(line -> info.getLineClass().isAssignableFrom(line.line().getLineClass()))
      .filter(line -> line.mixer().isLineSupported(info));
  }

  /** The preferred capture format: 48 kHz, 16-bit, stereo, signed, little-endian. */
  public final static AudioFormat IDEAL = new AudioFormat( 48000f, 16, 2, true, false);

  /**
   * Diagnostic entry point: prints all {@link #IDEAL}-format mixer lines to standard output.
   *
   * @param args command-line arguments (ignored)
   */
  public static void main(String[] args) {
    dump();
  }

  /**
   * Print all available {@link #IDEAL}-format mixer lines to standard output.
   * Useful for diagnosing which physical inputs the Java Sound API can see.
   */
  public static void dump() {
    List<MixerLine> mixerLines = allLinesMatching(IDEAL).toList();
    mixerLines.forEach(ml -> LOG.info("{}", ml));
  }
}
