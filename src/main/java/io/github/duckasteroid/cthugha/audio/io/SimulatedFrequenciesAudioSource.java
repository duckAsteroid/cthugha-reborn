package io.github.duckasteroid.cthugha.audio.io;

import io.github.duckasteroid.cthugha.audio.AudioSample;
import io.github.duckasteroid.cthugha.params.AbstractNode;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import java.awt.BorderLayout;
import java.io.IOException;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import javax.sound.sampled.AudioFormat;
import javax.swing.BoundedRangeModel;
import javax.swing.BoxLayout;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;

public class SimulatedFrequenciesAudioSource implements AudioSource {
  private final AudioFormat format = new AudioFormat(44100f, 16, 2, true, true);
  private long counter = 0;

  public DoubleParameter amplitude = new DoubleParameter("Amplitude", 0, 100, 1);

  public final List<Oscillator> oscillators;

  private static final short waveHeight =  Short.MAX_VALUE / 8;

  public SimulatedFrequenciesAudioSource(int oscillatorCount) {
    oscillators = IntStream.range(1, oscillatorCount + 1)
      .mapToObj(i -> new Oscillator("Oscillator "+ i, i * 261.63))
      .toList();

    JFrame frame = new JFrame("Simulated audio");
    frame.setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.Y_AXIS));
    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    oscillators.stream().forEach(osc -> {
      JPanel control = new JPanel();
      control.setLayout(new BorderLayout());
      control.add(new JLabel("Oscillator"), BorderLayout.NORTH);
      JSlider slider = new JSlider(20, 20000);
      slider.setMajorTickSpacing(1000);
      slider.setMinorTickSpacing(100);
      slider.setSize(500,32);
      BoundedRangeModel model = new BoundedRangeModel() {
        private boolean adjusting = false;
        @Override
        public int getMinimum() {
          return osc.frequency.getMin().intValue();
        }

        @Override
        public void setMinimum(int newMinimum) {

        }

        @Override
        public int getMaximum() {
          return osc.frequency.getMax().intValue();
        }

        @Override
        public void setMaximum(int newMaximum) {

        }

        @Override
        public int getValue() {
          return osc.frequency.getValue().intValue();
        }

        @Override
        public void setValue(int newValue) {
          osc.frequency.setValue(newValue);

          ChangeEvent event = new ChangeEvent(this);
          Arrays.stream(listeners.getListeners(ChangeListener.class))
            .forEach(l -> l.stateChanged(event));

        }

        @Override
        public void setValueIsAdjusting(boolean b) {
          adjusting = b;
        }

        @Override
        public boolean getValueIsAdjusting() {
          return adjusting;
        }

        @Override
        public int getExtent() {
          return 1;
        }

        @Override
        public void setExtent(int newExtent) {

        }

        @Override
        public void setRangeProperties(int value, int extent, int min, int max, boolean adjusting) {
          setValueIsAdjusting(adjusting);
          setValue(value);
        }

        private final EventListenerList listeners = new EventListenerList();

        @Override
        public void addChangeListener(ChangeListener x) {
          listeners.add(ChangeListener.class, x);
        }

        @Override
        public void removeChangeListener(ChangeListener x) {
          listeners.remove(ChangeListener.class, x);
        }
      };
      slider.setModel(model);
      control.add(slider, BorderLayout.CENTER);
      JLabel value = new JLabel();
      value.setText(model.getValue()+" Hz");
      model.addChangeListener(new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent e) {
          value.setText(model.getValue() + " Hz");
        }
      });
      control.add(value, BorderLayout.SOUTH);
      frame.add(control);
    });
    frame.setSize(500, 600); // 400 width and 500 height
    frame.setVisible(true);
  };

  public static class Oscillator extends AbstractNode {
    public DoubleParameter frequency = new DoubleParameter("Frequency (Hz)", 10, 20000);

    public Oscillator(String name, double hz) {
      super(name);
      initChildren(frequency);
      frequency.setValue(hz);
    }
  }

  @Override
  public double getAmplitude() {
    return amplitude.value;
  }

  @Override
  public void setAmplitude(double amplitude) {
    this.amplitude.value = amplitude;
  }

  @Override
  public AudioSample sample(int width) {
    ShortBuffer buffer = ShortBuffer.wrap(new short[width * format.getChannels()]);
    while(buffer.hasRemaining()) {
      double value = oscillators.stream().mapToDouble(osc -> osc.frequency.value)
        .map(freq -> Math.sin(2 * Math.PI *  counter * (freq / format.getSampleRate())) * waveHeight)
        .sum() / oscillators.size();
      value *= amplitude.value;
      counter++;
      for(int ch = 0 ; ch < format.getChannels(); ch++) {
        buffer.put((short) value);
        if (!buffer.hasRemaining()) {
          break;
        }
      }
    }
    buffer.flip();
    return new AudioSample(buffer, false, 1.0);
  }

  @Override
  public AudioFormat getFormat() {
    return format;
  }

  @Override
  public boolean isMono() {
    return false;
  }


  @Override
  public void close() throws IOException {

  }
}
