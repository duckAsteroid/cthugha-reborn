package io.github.duckasteroid.cthugha.display;
/**
 * This test generates a table of all available display modes, enters
 * full-screen mode, if available, and allows you to change the display mode.
 * The application should look fine under each enumerated display mode.
 * On UNIX, only a single display mode should be available, and on Microsoft
 * Windows, display modes should depend on direct draw availability and the
 * type of graphics card.
 */

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;

public class DisplayModeTest extends JFrame implements ActionListener,
  ListSelectionListener {

  private boolean waiting = false;
  private GraphicsDevice device;
  private DisplayMode originalDM;
  private JButton exit = new JButton("Exit");
  private JButton changeDM = new JButton("Set Display");
  private JLabel currentDM = new JLabel();
  private boolean isFullScreen = false;
  private DisplayMode nativeMode;
  private DisplayMode targetMode = new DisplayMode(800,600,32, DisplayMode.REFRESH_RATE_UNKNOWN);

  public DisplayModeTest(GraphicsDevice device) {
    super(device.getDefaultConfiguration());
    this.device = device;
    this.nativeMode = device.getDisplayMode();
    setTitle("Display Mode Test");
    originalDM = device.getDisplayMode();
    setDMLabel(originalDM);
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    // Make sure a DM is always selected in the list
    exit.addActionListener(this);
    changeDM.addActionListener(this);
    changeDM.setEnabled(device.isDisplayChangeSupported());
  }

  public void actionPerformed(ActionEvent ev) {
    Object source = ev.getSource();
    if (source == exit) {
      device.setDisplayMode(originalDM);
      System.exit(0);
    } else { // if (source == changeDM)
      // toggle DM
      if (nativeMode.getWidth() == device.getDisplayMode().getWidth() && nativeMode.getHeight() == device.getDisplayMode().getHeight()) {
        device.setDisplayMode(targetMode);
      }
      else {
        device.setDisplayMode(nativeMode);
      }
    }
  }

  public void valueChanged(ListSelectionEvent ev) {
    changeDM.setEnabled(device.isDisplayChangeSupported());
  }

  private void initComponents(Container c) {
    setContentPane(c);
    c.setLayout(new BorderLayout());
    // Current DM
    JPanel currentPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
    c.add(currentPanel, BorderLayout.NORTH);
    JLabel current = new JLabel("Current Display Mode : ");
    currentPanel.add(current);
    currentPanel.add(currentDM);
    // Display Modes
    JPanel modesPanel = new JPanel(new GridLayout(1, 2));
    c.add(modesPanel, BorderLayout.CENTER);

    // Controls
    JPanel controlsPanelA = new JPanel(new BorderLayout());
    modesPanel.add(controlsPanelA);
    JPanel controlsPanelB = new JPanel(new GridLayout(2, 1));
    controlsPanelA.add(controlsPanelB, BorderLayout.NORTH);
    // Exit
    JPanel exitPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
    controlsPanelB.add(exitPanel);
    exitPanel.add(exit);
    // Change DM
    JPanel changeDMPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
    controlsPanelB.add(changeDMPanel);
    changeDMPanel.add(changeDM);
    controlsPanelA.add(new JPanel(), BorderLayout.CENTER);
  }


  public void setDMLabel(DisplayMode newMode) {
    int bitDepth = newMode.getBitDepth();
    int refreshRate = newMode.getRefreshRate();
    String bd, rr;
    if (bitDepth == DisplayMode.BIT_DEPTH_MULTI) {
      bd = "Multi";
    } else {
      bd = Integer.toString(bitDepth);
    }
    if (refreshRate == DisplayMode.REFRESH_RATE_UNKNOWN) {
      rr = "Unknown";
    } else {
      rr = Integer.toString(refreshRate);
    }
    currentDM.setText(
      "width: " + newMode.getWidth() + " "
        + "height: " + newMode.getHeight() + " "
        + "bitDepth: " + bd + " "
        + "refresh: " + rr
    );
  }

  public void begin() {
    isFullScreen = device.isFullScreenSupported();
    setUndecorated(isFullScreen);
    setResizable(!isFullScreen);
    if (isFullScreen) {
      // Full-screen mode
      device.setFullScreenWindow(this);
      validate();
    } else {
      // Windowed mode
      pack();
      setVisible(true);
    }
  }

  public static void main(String[] args) {
    GraphicsEnvironment env = GraphicsEnvironment.
      getLocalGraphicsEnvironment();
    GraphicsDevice[] devices = env.getScreenDevices();
    // REMIND : Multi-monitor full-screen mode not yet supported
    for (int i = 0; i < 1 /* devices.length */; i++) {
      DisplayModeTest test = new DisplayModeTest(devices[i]);
      test.initComponents(test.getContentPane());
      test.begin();
    }
  }
}
