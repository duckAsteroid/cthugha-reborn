package io.github.duckasteroid.cthugha.display;

import java.awt.Dimension;
import java.awt.DisplayMode;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;

public class AwtDisplay { //implements Display
  public void init() {
    JFrame app = new JFrame();
    app.setIgnoreRepaint( true );
    app.setUndecorated( true );
    Dimension windowSize = new Dimension(3840,2160);
    app.setSize(windowSize);
    app.setVisible(true);
    // Add ESC listener to quit...
    app.addKeyListener( new KeyAdapter() {
      public void keyPressed( KeyEvent e ) {
        if( e.getKeyCode() == KeyEvent.VK_ESCAPE ) {
          //running = false;
        }
      }
    });
    app.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosed(WindowEvent e) {
        //running = false;
      }

    });

    // Get graphics configuration...
    GraphicsEnvironment ge =
      GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsDevice gd = ge.getDefaultScreenDevice();
    GraphicsConfiguration gc = gd.getDefaultConfiguration();

    // Change to full screen
    gd.setFullScreenWindow( app );
    if( gd.isDisplayChangeSupported() ) {
      gd.setDisplayMode(
        new DisplayMode( windowSize.width, windowSize.height, 32, DisplayMode.REFRESH_RATE_UNKNOWN )
      );
    }
  }
}
