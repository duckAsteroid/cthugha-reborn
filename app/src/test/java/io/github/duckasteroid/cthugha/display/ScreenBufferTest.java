package io.github.duckasteroid.cthugha.display;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Point;
import org.junit.jupiter.api.Test;

class ScreenBufferTest {
  // \ x 0  1  2  3
  // y -------------
  // 0 | 0  1  2  3
  // 1 | 4  5  6  7
  // 2 | 8  9  10 11

  ScreenBuffer subject = new ScreenBuffer(4,3);




  @Test
  void index() {
    assertEquals(0, subject.index(0,0));
    assertEquals(4, subject.index(0,1));
    assertEquals(5, subject.index(1,1));
    assertEquals(11, subject.index(3,2));
  }

  @Test
  void toIndex() {
    // same standard test as others (within limits)
    assertEquals(0, subject.toIndex(new Point(0,0), ScreenBuffer.PointWrapMode.LIMIT));
    assertEquals(4, subject.toIndex(new Point(0,1), ScreenBuffer.PointWrapMode.LIMIT));
    assertEquals(5, subject.toIndex(new Point(1,1), ScreenBuffer.PointWrapMode.LIMIT));
    assertEquals(11, subject.toIndex(new Point(3,2), ScreenBuffer.PointWrapMode.LIMIT));

    // now test LIMIT mode (outside range)
    assertEquals(3, subject.toIndex(new Point(5,0), ScreenBuffer.PointWrapMode.LIMIT), "X above limit of 3");
    assertEquals(8, subject.toIndex(new Point(0,8), ScreenBuffer.PointWrapMode.LIMIT), "Y above limit of 2");
    assertEquals(0, subject.toIndex(new Point(-3,0), ScreenBuffer.PointWrapMode.LIMIT), "X below limit of 0");
    assertEquals(2, subject.toIndex(new Point(2,-3), ScreenBuffer.PointWrapMode.LIMIT), "Y below limit of 0");
    assertEquals(11, subject.toIndex(new Point(11,11), ScreenBuffer.PointWrapMode.LIMIT), "X and Y above limits");
    assertEquals(0, subject.toIndex(new Point(-1,-1), ScreenBuffer.PointWrapMode.LIMIT), "X and Y below limits");

    // now test wrap mode
    assertEquals(0, subject.toIndex(new Point(4,0), ScreenBuffer.PointWrapMode.WRAP), "X above limit of 3");
    assertEquals(0, subject.toIndex(new Point(0,3), ScreenBuffer.PointWrapMode.WRAP), "Y above limit of 2");
    assertEquals(3, subject.toIndex(new Point(-1,0), ScreenBuffer.PointWrapMode.WRAP), "X below limit of 0");
    assertEquals(8, subject.toIndex(new Point(0,-1), ScreenBuffer.PointWrapMode.WRAP), "Y below limit of 0");
    assertEquals(0, subject.toIndex(new Point(8,9), ScreenBuffer.PointWrapMode.WRAP), "X and Y above limits");
    assertEquals(11, subject.toIndex(new Point(-1,-1), ScreenBuffer.PointWrapMode.WRAP), "X and Y below limits");
  }

  @Test
  void fromIndex() {
    assertEquals(new Point(0, 0), subject.fromIndex(0));
    assertEquals(new Point(0, 1), subject.fromIndex(4));
    assertEquals(new Point(1, 1), subject.fromIndex(5));
    assertEquals(new Point(3, 2), subject.fromIndex(11));

  }

  @Test
  void testWrapModeLimitConstrain() {
    final int MAX = 10;
    final int MIN = 0;
    assertEquals(0, ScreenBuffer.constrain(0,MIN,MAX));
    assertEquals(5, ScreenBuffer.constrain(5,MIN,MAX));
    assertEquals(10, ScreenBuffer.constrain(10,MIN,MAX));

    assertEquals(0, ScreenBuffer.constrain(-5,MIN,MAX));
    assertEquals(10, ScreenBuffer.constrain(15,MIN,MAX));
  }
}
