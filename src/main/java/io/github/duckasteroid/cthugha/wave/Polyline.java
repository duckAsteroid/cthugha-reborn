package io.github.duckasteroid.cthugha.wave;

import java.awt.Point;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Polyline {
  public final int[] xs;
  public final int[] ys;

  public Polyline(int size) {
    this.xs = new int[size];
    this.ys = new int[size];
  }

  public static Polyline wrap(Stream<Point> pointStream) {
    List<Point> list = pointStream.toList();
    Polyline result = new Polyline(list.size());
    IntStream.range(0, list.size()).forEach(i -> {
      result.setPoint(i, list.get(i));
    });
    return result;
  }

  public void setPoint(int index, Point p) {
    xs[index] = p.x;
    ys[index] = p.y;
  }

  public void set(int index, int x, int y) {
    xs[index] = x;
    ys[index] = y;
  }

  public boolean isEmpty() {
    return xs.length == 0;
  }

  public int size() {
    return xs.length;
  }
}
