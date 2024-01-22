package io.github.duckasteroid.cthugha.audio;

import static java.lang.Math.sqrt;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.stream.Collector;
import java.util.stream.Stream;
import javax.sound.sampled.LineUnavailableException;
import org.jtransforms.fft.DoubleFFT_1D;
import org.junit.jupiter.api.Test;

class FastFourierTransformTest {

  static class ShortCollector {
    public static Collector<Double ,ShortCollector,short[]> TO_ARRAY
      =Collector.of(ShortCollector::new, ShortCollector::add,
      ShortCollector::merge, c->c.get());

    short[] array=new short[100];
    int pos;

    public ShortCollector() {

    }

    public void add(double value) {
      int ix=pos;
      if(ix==array.length) array= Arrays.copyOf(array, ix*2);
      array[ix]=(short)value;
      pos=ix+1;
    }
    public ShortCollector merge(ShortCollector c) {
      int ix=pos, cIx=c.pos, newSize=ix+cIx;
      if(array.length<newSize) array=Arrays.copyOf(array, newSize);
      System.arraycopy(c.array, 0, array, ix, cIx);
      return this;
    }
    public short[] get() {
      return pos==array.length? array: Arrays.copyOf(array, pos);
    }
  }

  public static Stream<Double> testData() {
    int sampleRate = 48000; // per second
    double[] frequencies = new double[] { 440, 523.251, 659.255 }; // A minor
    Duration oneSec = Duration.of(1, ChronoUnit.SECONDS);

    return
      TestWaveformGenerator.createWaveForm(oneSec, sampleRate, Short.MAX_VALUE, frequencies);
  }

  @Test
  public void simpleTest()
    throws  LineUnavailableException, InterruptedException {

    short[] data = testData().collect(ShortCollector.TO_ARRAY);

    ByteBuffer encoded = ByteBuffer.allocate(data.length * 2);
    encoded.order(ByteOrder.BIG_ENDIAN);
    ShortBuffer floatBuffer = encoded.asShortBuffer();
    floatBuffer.put(data);


  }


  public static void main(String[] args) throws IOException {
    int size = 480;

    double[] data = testData().mapToDouble(Double::doubleValue).limit(size).toArray();

    Path rawOutput = Paths.get("raw-waveform.csv");
    try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(rawOutput, StandardCharsets.UTF_8,
      StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
      pw.println("Index,Value");
      for (int i = 0; i < data.length; i++) {
        pw.println(i+","+data[i]);
      }
    }

    DoubleFFT_1D fft = new DoubleFFT_1D(size);
    fft.realForward(data);

    Path rawFftOutput = Paths.get("raw-fft.csv");
    try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(rawFftOutput, StandardCharsets.UTF_8,
      StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
      pw.println("Index,Value");
      for (int i = 0; i < data.length; i++) {
        pw.println(i+","+data[i]);
      }
    }

    Path fftOutput = Paths.get("freq-fft.csv");
    try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(fftOutput, StandardCharsets.UTF_8,
      StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
      pw.println("Hz,Magnitude");
      for (int i = 0; i < data.length; i+=2) {
        double mag = sqrt(square(data[i]) + square(data[i+1]));
        double freq = bin_freq(i / 2, 48000, size);
        pw.println(freq +","+mag);
      }
    }

  }

  private static double square(double x) {
    return x * x;
  }

  private static double bin_freq(int bin_id, double sampleFreq, int N) {
    return (bin_id * sampleFreq / 2) / ( N / 2);
  }

}
