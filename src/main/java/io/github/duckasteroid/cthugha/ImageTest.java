package io.github.duckasteroid.cthugha;

import io.github.duckasteroid.cthugha.tab.Spiral;
import io.github.duckasteroid.cthugha.tab.Translate;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.DisplayMode;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.util.Random;
import javax.swing.JFrame;

public class ImageTest {
  private static boolean running;

  private static class ModifiableIndexedColorModel extends IndexColorModel {

    public ModifiableIndexedColorModel(int bits, int size, int[] cmap, int start, boolean hasalpha,
                                       int trans,
                                       int transferType) {
      super(bits, size, cmap, start, hasalpha, trans, transferType);
    }

    public void setRGBs() {

    }
  }

  public static void main(String[] args) {
    int step = 2;
    final int w = 3840 / step;
    final int h = 2160 / step;
    final int W_INDEX = 255;
    final int[] colors = new int[256];
    for(int i =0; i< colors.length; i++) {
      colors[i] = new Color(i,i,i).getRGB();
    }
    ScreenBuffer screenBuffer = new ScreenBuffer(w,h);
    screenBuffer.colors = colors;


    // 0 1 2
    // 3 4 5
    // 6 7 8
    // Translate
    final int[] example = new int[] {
      1,2,5,
      0,4,8,
      3,6,7
    };
    Dimension d = new Dimension(w,h);
    int[] table = new Spiral().generate(d);

    Translate translate = new Translate(d, table);

    running = true;
    Random random = new Random();
    // Create game window...
    JFrame app = new JFrame();
    app.setIgnoreRepaint( true );
    app.setUndecorated( true );
    Dimension windowSize = new Dimension(3840,2160);
    app.setSize(windowSize);
    app.setVisible(true);
    // Add ESC listener to quit...
    app.addKeyListener( new KeyAdapter() {
      public void keyPressed( KeyEvent e ) {
        if( e.getKeyCode() == KeyEvent.VK_ESCAPE )
          running = false;
      }
    });
    app.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosed(WindowEvent e) {
        running = false;
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

    // Create BackBuffer...
    app.createBufferStrategy( 2 );
    BufferStrategy buffer = app.getBufferStrategy();

    // Create off-screen drawing surface
    BufferedImage bi = gc.createCompatibleImage( w, h );

    // Objects needed for rendering...
    Graphics graphics = null;
    Graphics g2d = null;

    // Variables for counting frames per seconds
    int fps = 0;
    int frames = 0;
    long totalTime = 0;
    long curTime = System.currentTimeMillis();
    long lastTime = curTime;

    while(running) {
      try {
        // count Frames per second...
        lastTime = curTime;
        curTime = System.currentTimeMillis();
        totalTime += curTime - lastTime;
        if( totalTime > 1000 ) {
          totalTime -= 1000;
          fps = frames;
          frames = 0;
        }
        ++frames;

        // draw line
        BufferedImage image = screenBuffer.getBufferedImageView();
        Graphics imageGraphics = image.getGraphics();
        //imageGraphics.clearRect(0,0,w,h);
        imageGraphics.setColor(new Color(screenBuffer.colors[255]));
        int[] xPts = new int[w];
        int[] yPts = new int[w];
        yPts[0] = w/2;
        xPts[0] = 0;
        for(int x=1; x < w; x++) {
          xPts[x] = x;
          int jumpSize = 5;
          int yValue = yPts[x-1] + random.nextInt(jumpSize * 2) - jumpSize;
          yPts[x] = Math.min(h, Math.max(0, yValue));
        }
        imageGraphics.drawPolyline(xPts, yPts, w);
        imageGraphics.setColor(new Color(screenBuffer.colors[1]));
        imageGraphics.drawLine(0,random.nextInt(h),w,random.nextInt(h));
        imageGraphics.drawLine(0,random.nextInt(h),w,random.nextInt(h));
        imageGraphics.dispose();

        // draw our visualisation buffer
        screenBuffer.render(bi.getRaster());

        // draw on back buffer...
        g2d = buffer.getDrawGraphics();
        g2d.drawImage(bi, 0,0, windowSize.width, windowSize.height, null);
        // display frames per second...
        g2d.setFont( new Font( "Consolas", Font.PLAIN, 12 ) );
        g2d.setColor( Color.GREEN );
        g2d.drawString( String.format( "FPS: %s", fps ), 20, 20 );
        if (screenBuffer.pixels.length <= 9) {
          String[] viz = visualise(screenBuffer.pixels, w);
          for (int i = 0; i < viz.length; i++) {
            g2d.drawString(viz[i], 20, 40 + (12 * i));
          }
        }
        // Blit image and flip...
//        graphics = buffer.getDrawGraphics();
//        graphics.drawImage( bi, 0, 0, windowSize.width, windowSize.height, null );

        if( !buffer.contentsLost() )
          buffer.show();



        // process animation
        translate.transform(screenBuffer.pixels, screenBuffer.pixels);
        // evolve palette
        for(int i=0;i<screenBuffer.colors.length; i++) {
          Color color = new Color(screenBuffer.colors[i]);
          int red = color.getRed() - 1;
          if (red < 0) {
            red = 255;
          }

          screenBuffer.colors[i] = new Color(red, i, i).getRGB();
        }
        if(false) {
          Thread.sleep(10);
        }
      } catch (InterruptedException e) {
        running = false;
      } finally {
        // release resources
        if( graphics != null )
          graphics.dispose();
        if( g2d != null )
          g2d.dispose();
      }
    }

    gd.setFullScreenWindow( null );
    System.exit(0);
  }

  private static String[] visualise(byte[] barr, int width) {
    String[] result = new String[(barr.length + 1) / width];
    for(int i=0; i < result.length; i++) {
      StringBuilder line = new StringBuilder();
      for (int j=0; j < width; j++) {
        int b = barr[(width * i) + j] & 0xFF;
        String hex = Integer.toHexString(b).toUpperCase();
        while (hex.length() < 2) {
          hex = "0" + hex;
        }
        line.append(hex);
        if (j < width-1) {
          line.append(',');
        }
      }
      result[i] = line.toString();
    }
    return result;
  }
}
