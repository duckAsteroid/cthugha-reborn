package io.github.duckasteroid.cthugha;

import static io.github.duckasteroid.cthugha.stats.Statistics.to2DP;

import io.github.duckasteroid.cthugha.audio.AudioSample;
import io.github.duckasteroid.cthugha.audio.io.AudioSource;
import io.github.duckasteroid.cthugha.audio.io.SampledAudioSource;
import io.github.duckasteroid.cthugha.flame.Flame;
import io.github.duckasteroid.cthugha.img.RandomImageSource;
import io.github.duckasteroid.cthugha.keys.Keybind;
import io.github.duckasteroid.cthugha.map.MapFileReader;
import io.github.duckasteroid.cthugha.notify.NotificationRenderer;
import io.github.duckasteroid.cthugha.stats.Stats;
import io.github.duckasteroid.cthugha.stats.StatsFactory;
import io.github.duckasteroid.cthugha.tab.RandomTranslateSource;
import io.github.duckasteroid.cthugha.tab.Translate;
import io.github.duckasteroid.cthugha.wave.RadialWave;
import io.github.duckasteroid.cthugha.wave.SimpleWave;
import io.github.duckasteroid.cthugha.wave.SpeckleWave;
import io.github.duckasteroid.cthugha.wave.Wave;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.DisplayMode;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.LineUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JCthugha implements Runnable, Closeable {

	private static final Logger LOG = LoggerFactory.getLogger(JCthugha.class);
	public static final double AUTO_ROTATE_AMT = 1;

	//final AudioSource audioSource = new RandomSimulatedAudio(true);
	final AudioSource audioSource = new SampledAudioSource();
	int [] sound;

	ScreenBuffer buffer;

	MapFileReader reader;

	Translate translate;

	final Flame flame = new Flame();

	final SimpleWave wave = new SimpleWave();
	//final Wave wave = new VibratingCircleWave();
	final Wave wave2 = new RadialWave();
	final Wave speckles = new SpeckleWave();
	boolean doSpeckles = false;

	//final Wave fft = new SpectraBars(new FastFourierTransform(4800, audioSource.getFormat(), Channel.MONO_AVG));
	boolean doFFT = false;

	Stats frameRate = StatsFactory.deltaStats("frameRate");

	RandomTranslateSource translateSource = new RandomTranslateSource();

	RandomImageSource imageSource = new RandomImageSource(Paths.get("pcx"));

	BufferStrategy bufferStrategy;
	private BufferedImage screenImage;
	private Frame window;

	private boolean debug = true;
	private boolean notify = true;
	private Color debugColor = Color.GREEN;
	private Font debugFont = new Font("Courier New", Font.PLAIN, 12);
	private final NotificationRenderer notificationRenderer = new NotificationRenderer();

	public JCthugha() throws LineUnavailableException {

	}

	public void init(Dimension dims, BufferStrategy bufferStrategy, BufferedImage screenImage, Frame window, List<Keybind> keybinds) throws IOException {
		this.bufferStrategy = bufferStrategy;
		this.screenImage = screenImage;
		this.window = window;
		sound = new int[dims.width];
		buffer = new ScreenBuffer(dims.width, dims.height);
		translate = new Translate(dims, translateSource.generate(dims, true));
		Path currentWorkingDir = Paths.get("").toAbsolutePath();
		System.out.println(currentWorkingDir.normalize().toString());
		Path maps = Paths.get("maps");
		reader = new MapFileReader(maps);
		buffer.paletteMap = reader.random();
	}

	public synchronized void run() {
		try {
			// record the refresh rate (how quick this loop runs)
			frameRate.ping();

			// translate pixels in the screen buffer
			translate.transform(buffer.pixels, buffer.pixels);

			// flame
			flame.flame(buffer, buffer.getWriteableRaster());

			// get latest sound
			AudioSample audioSample = audioSource.sample(buffer.width);

			// wave
			wave.wave(audioSample, buffer);
			wave2.wave(audioSample, buffer);
			if (doSpeckles) {
				speckles.wave(audioSample, buffer);
			}
			if (doFFT) {
				//fft.wave(audioSample, buffer);
			}

			// render the buffer onto the screen ready image
			buffer.render(screenImage);

			// draw onto back buffer
			Graphics g2d = bufferStrategy.getDrawGraphics();
			g2d.drawImage(screenImage, 0,0, window.getWidth(), window.getHeight(), null);
			notificationRenderer.render(new Dimension(window.getWidth(), window.getHeight()), g2d);
			if (debug) {
				renderDebugInfo(g2d);
			}
			g2d.dispose();

			// flip
			if( !bufferStrategy.contentsLost() )
				bufferStrategy.show();
		} catch(Throwable t) {
			LOG.error("Processing main loop", t);
		}
	}

	private void renderDebugInfo(Graphics g2d) {
		g2d.setFont(debugFont);
		int y = 50;
		int fontHeight = g2d.getFontMetrics().getHeight();
		renderDebugString(g2d, frameRate.getFrameRate(), y);
		y+=fontHeight;
		renderDebugString(g2d, "Amplifier: "+(audioSource.getAmplification() * 100), y);
		y+=fontHeight;
		renderDebugString(g2d, "window="+new Dimension(window.getWidth(), window.getHeight())+"; buffer="+buffer.getDimensions(), y);
		y+=fontHeight;
		renderDebugString(g2d, translateSource.getLastGenerated(),  y);
		y+=fontHeight;
		renderDebugString(g2d, buffer.paletteMap.getName(), y);
		y+=fontHeight;
		g2d.drawImage(buffer.paletteMap.getPaletteImage(), 0, y, window.getWidth(), 10, null);
	}

	private void renderDebugString(Graphics g2d, String message, int y) {
		//g2d.setColor(Color.WHITE);
		//g2d.drawString(message, 9, y-1);
		g2d.setColor(Color.BLACK);
		g2d.drawString(message, 11, y+1);
		g2d.setColor(debugColor);
		g2d.drawString(message, 10, y);
	}

	public void notify(String message) {
		if (notify) {
			notificationRenderer.notify(message);
		}
	}

	public void newTranslation(boolean newMap) {
		translate.changeTable(translateSource.generate(buffer.getDimensions(), newMap));
		notify(translateSource.getLastGenerated());
	}

	public void changeAmplitude(double ratio) {
		audioSource.setAmplification(audioSource.getAmplification() * ratio);
		notify("Amplitude = "+audioSource.getAmplification());
	}

	public void newPalette() {
		try {
			buffer.paletteMap = reader.random();
			notify(buffer.paletteMap.getName());
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}

	public void toggleDebug() {
		this.debug = !debug;
		notify("debug="+debug);
	}

	public void toggleSpeckle() {
		this.doSpeckles = !doSpeckles;
		notify("speckles="+doSpeckles);
	}

	public void rotate( double degrees) {
		this.wave.rotate(degrees);
		notify("rotate="+degrees);
	}

	@Override
	public void close() throws IOException {
		audioSource.close();
	}

	public static void main(String[] args) throws LineUnavailableException, IOException {
		GraphicsEnvironment localGraphicsEnvironment =
			GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice gd = localGraphicsEnvironment.getDefaultScreenDevice();
		DisplayMode displayMode = gd.getDisplayMode();
		Dimension screenSize = new Dimension(640,480); //
		//Dimension screenSize =  new Dimension(displayMode.getWidth(), displayMode.getHeight());
		System.out.println(screenSize);
		int fract = 1;
		Dimension cthughaBufferSize = new Dimension(screenSize.width / fract, screenSize.height / fract);
		final Frame f = new Frame();

		final JCthugha jCthugha = new JCthugha();
		final List<Keybind> keybindings = Arrays.asList(
			new Keybind('a', "Toggle audio source", (e) -> jCthugha.toggleAudioSource()),
			new Keybind('s', "Toggle speckle wave", (e) -> jCthugha.toggleSpeckle()),
			new Keybind('n', "Toggle notifications", (e) -> jCthugha.toggleNotifications()),
			new Keybind('t', 'T', "Randomise the translation. Shift T to really randomise it", (e) -> jCthugha.newTranslation(e.isShiftDown())),
			new Keybind('p', "Change the palette", (e) -> jCthugha.newPalette()),
			new Keybind( 'd', "Toggle debug", (e) -> jCthugha.toggleDebug()),
			new Keybind(',', "Spin waves left", (e) -> jCthugha.wave.autoRotate(-AUTO_ROTATE_AMT)),
			new Keybind( '.', "Spin waves right", (e) -> jCthugha.wave.autoRotate(AUTO_ROTATE_AMT)),
			new Keybind('<', "Rotate wave 10 degrees left", (e)-> jCthugha.rotate(-10)),
			new Keybind('>', "Rotate wave 10 degrees left", (e)-> jCthugha.rotate(10)),
			new Keybind('x', "Flash fill the screen", (e) -> Arrays.fill(jCthugha.buffer.pixels, (byte)255)),
			new Keybind( 'i', "Flash a random image", (e) -> jCthugha.flashImage()),
			new Keybind('u', 'U',"Increase amplitude", (e) -> {
				if(e.isShiftDown()) jCthugha.changeAmplitude(1.1);
				else jCthugha.changeAmplitude(1.01);
			}),
			new Keybind('j', 'J', "Decrease amplitude", (e) -> {
				if(e.isShiftDown()) jCthugha.changeAmplitude(0.9);
				else jCthugha.changeAmplitude(0.99);
			}),
			new Keybind( 'f', "Toggle fullscreen (NOT CURRENTLY WORKING)", (e) -> {
				GraphicsDevice graphicsDevice = f.getGraphicsConfiguration().getDevice();
				if (graphicsDevice.isFullScreenSupported()) {
					if (graphicsDevice.getFullScreenWindow() == null) {
						DisplayMode mode = graphicsDevice.getDisplayMode();
						f.setSize(mode.getWidth(), mode.getHeight());
						graphicsDevice.setFullScreenWindow(f.getOwner());
					} else {
						graphicsDevice.setFullScreenWindow(null);
					}
				} else {
					System.out.println("Full screen not supported");
				}
			}));

		ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(1);

		//f.add(jCthugha);         //adding a new Button.
		f.setSize(screenSize.width, screenSize.height);        //setting size.
		f.setTitle("Java Cthugha");  //setting title.
		//f.setLayout(null);   //set default layout for frame.
		//f.setUndecorated(true);
		f.setIgnoreRepaint(true);
		//f.setResizable(false);
		f.setVisible(true);           //set frame visibility true
		f.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				executorService.shutdownNow();
				f.dispose();
				try {
					jCthugha.close();
					System.out.println(StatsFactory.getStatisticsSummary());
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		});
		f.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				for(Keybind bind : keybindings) {
					if (bind.isFired(e)) {
						bind.handle(e);
						break;
					}
				}
			}
		});

		f.createBufferStrategy(3);

		// initialise cthugha
		GraphicsConfiguration graphicsConfiguration = gd.getDefaultConfiguration();
		BufferedImage screenCompatibleImage =
			graphicsConfiguration.createCompatibleImage(cthughaBufferSize.width,
				cthughaBufferSize.height);
		jCthugha.init(cthughaBufferSize, f.getBufferStrategy(), screenCompatibleImage, f, keybindings);

		executorService.scheduleAtFixedRate(jCthugha, 100, 1000/60, TimeUnit.MILLISECONDS);
	}

	private void toggleNotifications() {
		notify = !notify;
		notificationRenderer.notify("notifications="+notify);
	}

	private void toggleAudioSource() {
		audioSource.nextSource();
		notify(audioSource.getSourceName());
	}

	private void flashImage() {
		try {
			BufferedImage flash = imageSource.nextImage();
			Graphics2D graphics = buffer.getBufferedImageView().createGraphics();
			graphics.drawImage(flash, 0,0, buffer.width, buffer.height, null);
			graphics.dispose();
		} catch (IOException e) {
			LOG.error("Error flash image", e);
		}
	}
}

