package io.github.duckasteroid.cthugha.display;
import java.awt.*;
import java.awt.image.BufferStrategy;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JFrame;

public class MultiBufferTest {

  private static Color[] COLORS = new Color[] {
    Color.red, Color.blue, Color.green, Color.white, Color.black,
    Color.yellow, Color.gray, Color.cyan, Color.pink, Color.lightGray,
    Color.magenta, Color.orange, Color.darkGray };

  Frame mainFrame;

  public MultiBufferTest(int numBuffers, GraphicsDevice device) {
    try {
      //DisplayMode preferred = new DisplayMode(1280,720,32, DisplayMode.REFRESH_RATE_UNKNOWN);
      DisplayMode preferred = new DisplayMode(800,600,32, DisplayMode.REFRESH_RATE_UNKNOWN);
      Dimension resolution = new Dimension(preferred.getWidth(), preferred.getHeight());

      DisplayMode nativeMode = device.getDisplayMode();
      System.out.println(Stream.of(device.getDisplayModes())
        .map(mode -> summarize(mode, nativeMode))
        .collect(Collectors.joining("\n")));

      GraphicsConfiguration gc = device.getDefaultConfiguration();
      mainFrame = new Frame(gc);
      //mainFrame.setContentPane(mainFrame.getContentPane());
      mainFrame.setUndecorated(true);
      mainFrame.setResizable(false);
      //mainFrame.setBounds(new Rectangle(resolution));
      device.setFullScreenWindow(mainFrame);
      if (device.isDisplayChangeSupported()) {
        device.setDisplayMode(preferred);
        mainFrame.setSize(resolution);
       // mainFrame.validate();
        mainFrame.setIgnoreRepaint(true);
        System.out.println("Selected:" + summarize(preferred, nativeMode));
      }
      Rectangle bounds = mainFrame.getBounds();
      System.out.println(bounds);
      mainFrame.createBufferStrategy(numBuffers);
      BufferStrategy bufferStrategy = mainFrame.getBufferStrategy();

      for (float lag = 50.0f; lag > 0.00000006f; lag = lag / 1.33f) {
        for (int i = 0; i < numBuffers; i++) {
          Graphics g = bufferStrategy.getDrawGraphics();
          if (!bufferStrategy.contentsLost()) {
            g.setColor(COLORS[i]);
            //Rectangle clipBounds = mainFrame.getBounds();
            g.fillRect(bounds.x, bounds.y, bounds.width * 2, bounds.height * 2);
            bufferStrategy.show();
            g.dispose();
          }
          try {
            Thread.sleep((int)lag);
          } catch (InterruptedException e) {}
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      device.setFullScreenWindow(null);
    }
  }

  private static String summarize(DisplayMode mode, DisplayMode nativeMode) {
    double nativeRatio = (double) nativeMode.getWidth() / (double) nativeMode.getHeight();
    double ratio = (double) mode.getWidth() / (double) mode.getHeight();
    double upper = nativeRatio * 1.0000001;
    double lower = nativeRatio * 0.9999999;
    boolean nativeMultiple = upper > ratio & ratio > lower;
    return mode.getWidth() + "x"+mode.getHeight()+ "("+String.format("%.2f", ratio)+")" +
      ":"+mode.getBitDepth()+
      " @"+mode.getRefreshRate() +
      (nativeMultiple ? " *" : "");
  }

  public static void main(String[] args) {
    try {
      int numBuffers = 2;
      if (args != null && args.length > 0) {
        numBuffers = Integer.parseInt(args[0]);
        if (numBuffers < 2 || numBuffers > COLORS.length) {
          System.err.println("Must specify between 2 and "
            + COLORS.length + " buffers");
          System.exit(1);
        }
      }
      GraphicsEnvironment env = GraphicsEnvironment.
        getLocalGraphicsEnvironment();
      GraphicsDevice device = env.getDefaultScreenDevice();
      MultiBufferTest test = new MultiBufferTest(numBuffers, device);
    } catch (Exception e) {
      e.printStackTrace();
    }
    System.exit(0);
  }
}
