package io.github.duckasteroid.cthugha.audio.io;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LineAcquirer {

  private static final Logger LOG = LoggerFactory.getLogger(LineAcquirer.class);
  private static Supplier<NoSuchLineException>
    noSuchLineExceptionSupplier = () -> new NoSuchLineException();

  private static Optional<SourceDataLine> getLine(Mixer mixer, DataLine.Info info) {
    try {
      Line line = mixer.getLine(info);
      if (line instanceof SourceDataLine) {
        return Optional.of((SourceDataLine)line);
      }
    } catch (LineUnavailableException e) {
      LOG.error("Error acquiring line",e);
    }
    return Optional.empty();
  }

  public static SourceDataLine acquireOutput(String mixerName, AudioFormat required)
    throws NoSuchLineException {
    Mixer mixer = getMixer(mixerName);
    noSuchLineExceptionSupplier =
      () -> new NoSuchLineException(mixerName, SourceDataLine.class, required);
    return Arrays.stream(mixer.getSourceLineInfo())
      .filter(DataLine.Info.class::isInstance)
      .map(info -> (DataLine.Info)info)
      .filter(lineFilter(SourceDataLine.class, required))
      .map(info -> getLine(mixer, info))
      .findFirst().orElseThrow(noSuchLineExceptionSupplier).orElseThrow(noSuchLineExceptionSupplier);
  }

  public static SourceDataLine acquireInput(String mixerName, AudioFormat required)
    throws NoSuchLineException {
    Mixer mixer = getMixer(mixerName);
    return Arrays.stream(mixer.getTargetLineInfo())
      .filter(DataLine.Info.class::isInstance)
      .map(info -> (DataLine.Info)info)
      .filter(lineFilter(TargetDataLine.class, required))
      .map(info -> getLine(mixer, info))
      .findFirst().orElseThrow(noSuchLineExceptionSupplier).orElseThrow(noSuchLineExceptionSupplier);
  }

  public static <T extends DataLine> Predicate<DataLine.Info> lineFilter(Class<T> type, AudioFormat required) {
    return info -> info.getLineClass().isAssignableFrom(type) &&
      (required == null || info.isFormatSupported(required)); // format matches if specified
  }

  public static Mixer getMixer(String namePattern) {
    Pattern pattern = Pattern.compile(namePattern);
    Predicate<String> stringPredicate = pattern.asMatchPredicate();
    Predicate<Mixer.Info> matcher = (info) -> stringPredicate.test(info.getName());
    return mixers().filter(matcher).findFirst().map(AudioSystem::getMixer)
      .orElseThrow(() -> new IllegalArgumentException("No mixer matching " + namePattern));
  }

  public static Stream<Mixer.Info> mixers() {
    return Arrays.stream(AudioSystem.getMixerInfo());
  }

  public static class NoSuchLineException extends Throwable {
    public NoSuchLineException() {
      super();
    }
    public <T extends DataLine> NoSuchLineException(String mixer, Class<T> clazz, AudioFormat required) {
      super("Unable to find "+clazz.getSimpleName()+" for mixer: "+mixer+" format:"+required);
    }

  }
}
