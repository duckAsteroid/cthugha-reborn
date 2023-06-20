package io.github.duckasteroid.cthugha.kaleidoscope;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSlider;

public class TestGraphics extends JFrame {
  private JSlider slider1;
  private JSlider slider2;

  private JPanel canvas;

  public TestGraphics() {
    setTitle("Test graphics");
    setLayout(new GridLayout(2,2));
    Container contentPane = getContentPane();
    canvas = new JPanel();
    canvas.setBackground(Color.CYAN);
    contentPane.add(canvas);
    slider1 = new JSlider();
    slider1.setMaximumSize(new Dimension(50, 768));
    slider1.setOrientation(JSlider.VERTICAL);
    contentPane.add(slider1);

    slider2 = new JSlider();
    slider2.setMaximumSize(new Dimension(1024,50));
    contentPane.add(slider2);
  }

  public static void main(String[] args) {
    TestGraphics frame = new TestGraphics();
    frame.setSize(1024,768);
    frame.setVisible(true);
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
  }
}
