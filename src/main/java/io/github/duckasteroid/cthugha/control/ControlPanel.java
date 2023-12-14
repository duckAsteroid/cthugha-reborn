package io.github.duckasteroid.cthugha.control;

import io.github.duckasteroid.cthugha.audio.io.sim.Oscillator;
import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.List;
import javax.swing.BoundedRangeModel;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;

public class ControlPanel {
  private static JFrame frame;

  public static void show(List<Oscillator> oscillators) {
    frame = new JFrame("Simulated audio");
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
      BoundedRangeModel model = new ParameterBoundedRangeModel(osc.frequency);
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

  public static void close() {
    if (frame != null) {
      frame.setVisible(false);
      frame.dispose();
    }
  }

}
