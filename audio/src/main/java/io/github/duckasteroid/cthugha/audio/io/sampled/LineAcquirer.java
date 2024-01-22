package io.github.duckasteroid.cthugha.audio.io.sampled;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

public class LineAcquirer {
  public record MixerLine(Mixer mixer, Line.Info line) {
    public TargetDataLine getTargetDataLine() throws LineUnavailableException {
      return (TargetDataLine) mixer.getLine(line);
    }
    @Override
    public String toString() {
      return mixer.getMixerInfo().getName() + ":"+ line.toString();
    }
  };
  private static final Logger LOG = LoggerFactory.getLogger(LineAcquirer.class);
  public List<MixerLine> allLines() {
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

  public List<MixerLine> allLinesMatching(Class<? extends DataLine> type, AudioFormat format) {
    DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
    return allLinesMatching(info);
  }
  public List<MixerLine> allLinesMatching(DataLine.Info info) {
    return allLines().stream()
      .filter(line -> line.mixer.isLineSupported(info))
      .toList();
  }

  public static void main(String[] args) {
    LineAcquirer laq = new LineAcquirer();
    laq.allLinesMatching(TargetDataLine.class, SampledAudioSource.IDEAL).forEach(System.out::println);
  }
}
