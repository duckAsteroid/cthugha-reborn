package io.github.duckasteroid.cthugha.audio;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.TargetDataLine;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.mockito.Mockito.*;
class AudioBufferTest {

  public static AudioFormat format = new AudioFormat(41000,16,2,true, false);

  public static short[] testData(int min, int max, int length) {
    short diff = (short) (max - min);
    short stepSize = (short) (diff / length);
    short[] result = new short[length];
    for(int i = 0; i < length; i++) {
      result[i] = (short) (min + (stepSize * i));
    }
    return result;
  }

  public static TargetDataLine mockAudioLine() {
    short[] left = testData(0, 1000, 100);
    short[] right = testData(-1000, 0, 100);
    TargetDataLine mockTargetDataLine = mock(TargetDataLine.class);
    when(mockTargetDataLine.available()).thenReturn(100);
    doAnswer(new Answer() {
      public Object answer(InvocationOnMock invocation) {
        Object[] args = invocation.getArguments();
        byte[] b = (byte[])args[0];
        int off = (int)args[1];
        int len = (int)args[2];
        System.out.println("buf="+b.length);
        ByteBuffer wrap = ByteBuffer.wrap(b, off, len);
        wrap.order(ByteOrder.LITTLE_ENDIAN);
        ShortBuffer data = wrap.asShortBuffer();
        System.out.println("rem="+data.remaining());
        int i = 0;
        while(data.hasRemaining()) {
          data.put(left[i]);
          data.put(right[i]);
          i++;
        }
        return data.position() * 2;
      }}).when(mockTargetDataLine).read(any(byte[].class), anyInt(), anyInt());
    return mockTargetDataLine;
  }

  @Test
  void readFrom() {
    TargetDataLine mockTargetDataLine = mockAudioLine();
    AudioBuffer subject = new AudioBuffer(format, Duration.ofSeconds(1));
    AudioSample audioSample =
      subject.readFrom(mockTargetDataLine, 25);
    assertNotNull(audioSample);
    assertFalse(audioSample.mono);
  }

  @Test
  void readAudioPoints() {
    TargetDataLine mockTargetDataLine = mockAudioLine();
    AudioBuffer subject = new AudioBuffer(format, Duration.ofSeconds(1));
    AudioSample audioSample =
      subject.readFrom(mockTargetDataLine, 25);
    List<AudioPoint> audioPoints = audioSample.streamPoints().collect(Collectors.toList());
    assertNotNull(audioPoints);
    assertEquals(25, audioPoints.size());

  }

  @Test
  void intensity() {
    TargetDataLine mockTargetDataLine = mockAudioLine();
    AudioBuffer subject = new AudioBuffer(format, Duration.ofSeconds(1));
    AudioSample audioSample =
      subject.readFrom(mockTargetDataLine, 25);
    double intensity = audioSample.intensity(Channel.MONO_AVG);
    assertEquals(380.0, intensity);
  }

  @Test
  void normalise() {
    float actual = AudioBuffer.normalise((short)0);
    assertEquals(0f, actual, 0.0001f);
    actual = AudioBuffer.normalise(Short.MAX_VALUE);
    assertEquals(1f, actual, 0.0001f);
    actual = AudioBuffer.normalise(Short.MIN_VALUE);
    assertEquals(-1f, actual, 0.0001f);
  }

  //@Test
  void transpose() {
    int actual = AudioBuffer.transpose((short) 0, 512);
    assertEquals(0, actual);
    actual = AudioBuffer.transpose(Short.MAX_VALUE, 512);
    assertEquals(512, actual);
    actual = AudioBuffer.transpose(Short.MIN_VALUE, 512);
    assertEquals(-511, actual);
  }
}
