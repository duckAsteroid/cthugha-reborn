package io.github.duckasteroid.cthugha;

import static org.junit.jupiter.api.Assertions.*;

import io.github.duckasteroid.cthugha.tab.Translate;
import java.awt.Dimension;
import org.junit.jupiter.api.Test;

class TranslateTest {
  final int[] noop = new int[] {
    1,2,3,4,5,6,7,8,9
  };
  final int[] example = new int[] {
    2,3,6,
    5,1,9,
    4,7,8
  };

  final byte[] source() {
    return new byte[] {1,2,3,4,5,6,7,8,9};
  }

  final byte[] empty() {
    return new byte[]{0,0,0,0,0,0,0,0,0};
  }

  final Dimension testDims = new Dimension(3,3);

  //@Test
  void transform() {
    Translate subject = new Translate(testDims, noop);
    byte[] src = source();
    byte[] dest = empty();
    subject.transform(src,dest);
    assertArrayEquals(src, dest);

    subject = new Translate(testDims, example);
    subject.transform(src, dest);
    assertArrayEquals(new byte[]{2,3,6,  5,1,9,  4,7,8}, dest);
  }

  @Test
  void animate(){
    int[][] result = Translate.changeTable(new int[]{0,100,500,0,-100}, new int[]{100,0,600,100,0}, 10);
    assertEquals(11, result.length);

  }
}
