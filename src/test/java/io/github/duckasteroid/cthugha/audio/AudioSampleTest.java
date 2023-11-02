package io.github.duckasteroid.cthugha.audio;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ShortBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;

class AudioSampleTest {

  short[] stereoData = new short[] {
    0,1,
    1,2,
    2,3,
    3,4,
    4,5,
    5,6,
    6,7,
    7,8
  };

  short[] monoData = new short[] {
    0,
    1,
    2,
    3,
    4,
    5,
    6,
    7
  };

  @Test
  public void stereoTest() {
    AudioSample subject = new AudioSample(ShortBuffer.wrap(stereoData), false, 1.0);
    assertEquals(8, subject.size());
    List<AudioPoint> list = subject.streamPoints().toList();
    assertEquals(8, list.size());
    for (int i = 0; i < list.size(); i++) {
      AudioPoint audioPoint = list.get(i);
      assertEquals(stereoData[i * 2], audioPoint.value(Channel.LEFT));
      assertEquals(stereoData[(i * 2) + 1], audioPoint.value(Channel.RIGHT));
    }
  }

  @Test
  public void monoTest() {
    AudioSample subject = new AudioSample(ShortBuffer.wrap(monoData), true, 1.0);
    assertEquals(8, subject.size());
    List<AudioPoint> list = subject.streamPoints().toList();
    assertEquals(8, list.size());
    for (int i = 0; i < list.size(); i++) {
      AudioPoint audioPoint = list.get(i);
      assertEquals(monoData[i], audioPoint.value(Channel.LEFT));
      assertEquals(monoData[i], audioPoint.value(Channel.RIGHT));
    }
  }

}
