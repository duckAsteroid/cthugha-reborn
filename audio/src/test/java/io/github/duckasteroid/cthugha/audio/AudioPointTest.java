package io.github.duckasteroid.cthugha.audio;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AudioPointTest {
  @Test
  void normalise() {
    double actual = AudioPoint.normalise((short)0);
    assertEquals(0d, actual, 0.0001d);
    actual = AudioPoint.normalise(Short.MAX_VALUE);
    assertEquals(1d, actual, 0.0001d);
    actual = AudioPoint.normalise(Short.MIN_VALUE);
    assertEquals(-1d, actual, 0.0001d);
  }

  //@Test
  void transpose() {
    int actual = AudioPoint.transpose((short) 0, 512);
    assertEquals(0, actual);
    actual = AudioPoint.transpose(Short.MAX_VALUE, 512);
    assertEquals(512, actual);
    actual = AudioPoint.transpose(Short.MIN_VALUE, 512);
    assertEquals(-511, actual);
  }
}
