package io.github.duckasteroid.cthugha.audio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.asteroid.duck.opengl.util.audio.AudioDataSource;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import javax.sound.sampled.AudioFormat;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

class AudioBufferTest {

  public static AudioFormat format = new AudioFormat(41000, 16, 2, true, false);

  public static short[] testData(int min, int max, int length) {
    short diff = (short) (max - min);
    short stepSize = (short) (diff / length);
    short[] result = new short[length];
    for (int i = 0; i < length; i++) {
      result[i] = (short) (min + (stepSize * i));
    }
    return result;
  }

  public static AudioDataSource mockAudioSource() {
    short[] left = testData(0, 1000, 100);
    short[] right = testData(-1000, 0, 100);
    AudioDataSource mock = mock(AudioDataSource.class);
    when(mock.available()).thenReturn(400); // 100 stereo 16-bit samples = 400 bytes
    doAnswer(new Answer<Integer>() {
      public Integer answer(InvocationOnMock invocation) {
        byte[] b = (byte[]) invocation.getArguments()[0];
        int off = (int) invocation.getArguments()[1];
        int len = (int) invocation.getArguments()[2];
        ByteBuffer wrap = ByteBuffer.wrap(b, off, len);
        wrap.order(ByteOrder.LITTLE_ENDIAN);
        ShortBuffer data = wrap.asShortBuffer();
        int i = 0;
        while (data.hasRemaining() && i < left.length) {
          data.put(left[i]);
          data.put(right[i]);
          i++;
        }
        return data.position() * 2;
      }
    }).when(mock).read(any(byte[].class), anyInt(), anyInt());
    return mock;
  }

  @Test
  void readFrom() {
    AudioDataSource mockDs = mockAudioSource();
    AudioBuffer subject = new AudioBuffer(format, Duration.ofSeconds(1));
    AudioSample audioSample = subject.readFrom(mockDs, 25);
    assertNotNull(audioSample);
    assertFalse(audioSample.channels == 0);
  }

  @Test
  void readAudioPoints() {
    AudioDataSource mockDs = mockAudioSource();
    AudioBuffer subject = new AudioBuffer(format, Duration.ofSeconds(1));
    AudioSample audioSample = subject.readFrom(mockDs, 25);
    List<AudioPoint> audioPoints = audioSample.streamPoints().collect(Collectors.toList());
    assertNotNull(audioPoints);
    assertEquals(25, audioPoints.size());
  }

  @Test
  void intensity() {
    AudioDataSource mockDs = mockAudioSource();
    AudioBuffer subject = new AudioBuffer(format, Duration.ofSeconds(1));
    AudioSample audioSample = subject.readFrom(mockDs, 25);
    double intensity = audioSample.intensity(Channel.MONO_AVG);
    assertEquals(380.0, intensity);
  }
}
