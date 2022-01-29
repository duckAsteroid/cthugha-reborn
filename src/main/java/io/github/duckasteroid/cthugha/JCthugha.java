package io.github.duckasteroid.cthugha;

import io.github.duckasteroid.cthugha.audio.SampledAudioSource;
import io.github.duckasteroid.cthugha.flame.Flame;
import io.github.duckasteroid.cthugha.map.MapFileReader;
import io.github.duckasteroid.cthugha.stats.Stats;
import io.github.duckasteroid.cthugha.stats.StatsFactory;
import io.github.duckasteroid.cthugha.tab.RandomTranslateSource;
import io.github.duckasteroid.cthugha.tab.Translate;
import io.github.duckasteroid.cthugha.wave.SimpleWave;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.DisplayMode;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Panel;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.ImageObserver;
import java.awt.image.IndexColorModel;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.LineUnavailableException;

public class JCthugha extends Panel implements Runnable, ImageObserver, Closeable {

	final SampledAudioSource audioSource = new SampledAudioSource();
	int [] sound;

	ScreenBuffer buffer;

	MapFileReader reader;

	Translate translate;

	final Flame flame = new Flame();

	final SimpleWave wave = new SimpleWave().wave(10);

	Stats timeStatistics = StatsFactory.deltaStats("frameRate");

	RandomTranslateSource translateSource = new RandomTranslateSource();

	ExecutorService backgroundTasks = Executors.newFixedThreadPool(2);

	private BufferedImage screen;
	private final BufferStrategy bufferStrategy;

	public JCthugha(BufferStrategy bufferStrategy) throws LineUnavailableException {
		this.bufferStrategy = bufferStrategy;
	}

	public void init(Dimension bufferSize) throws IOException {

		Path maps = Paths.get("maps");
		reader = new MapFileReader(maps);

		IndexColorModel indexColorModel = reader.random();
		sizeChanged(bufferSize);
		screen = paletteChanged(indexColorModel);

		addKeyListener(new KeyAdapter() {
			@Override
			public void keyTyped(KeyEvent e) {
				if (e.getKeyChar() == 'p' || e.getKeyChar() == 'P') {
					backgroundTasks.submit(() -> {
						try {
							IndexColorModel random = reader.random();
							paletteChanged(random);
						} catch (IOException ex) {
							ex.printStackTrace();
						}
					});
				}
				else if(e.getKeyChar() == 's' || e.getKeyChar() == 'S') {
					System.out.println(StatsFactory.getStatisticsSummary());
					System.out.println(translateSource.getLastGenerated());
				}
				else if(e.getKeyChar() == 't' || e.getKeyChar() == 'T') {
					boolean newSource = e.getKeyChar() == 'T';
					backgroundTasks.submit(() -> {
						translate.changeTable(translateSource.generate(bufferSize, newSource), 1);
						System.out.println(translateSource.getLastGenerated());
					});
				}
				else if(e.getKeyChar() == 'x' || e.getKeyChar() == 'X') {
					findParentFrame().dispose();
				}
			}
		});
	}

	private void sizeChanged(Dimension dims) {
		sound = new int[dims.width];

		buffer = new ScreenBuffer(dims.width, dims.height);

		translate = new Translate(dims, translateSource.generate(dims, true));
	}

	private BufferedImage paletteChanged(IndexColorModel icm) {
		DataBuffer dataBuffer = new DataBufferByte(buffer.pixels, buffer.pixels.length, 0);
		SampleModel sampleModel = new PixelInterleavedSampleModel(
			DataBuffer.TYPE_BYTE, buffer.width, buffer.height, 1, buffer.width, new int[] {0});

		WritableRaster raster = Raster.createWritableRaster(
			sampleModel, dataBuffer, null);

		return new BufferedImage(icm, raster, false, null);
	}

	public synchronized void run() {

			// get sound
			audioSource.sample(sound, buffer.width, buffer.height);

			// translate
			translate.transform(buffer.pixels, buffer.pixels);

			// flame
			flame.flame(buffer);

			//wave
			wave.wave(sound, buffer);

			int[] pixles = new int[buffer.pixels.length];
			System.arraycopy(buffer.pixels, 0, pixles, 0, pixles.length);
			screen.getRaster().setPixels(0,0,buffer.width, buffer.height, pixles);

			// draw
			Graphics g = bufferStrategy.getDrawGraphics();
			if (!bufferStrategy.contentsLost()) {
				Dimension size = getSize();
				//Rectangle clipBounds = mainFrame.getBounds();
				g.drawImage(screen,0, 0, size.width, size.height, null);
				bufferStrategy.show();
				g.dispose();
			}

	}

	public Frame findParentFrame() {
		Container parent = getParent();
		while (parent != null) {
			if ((parent instanceof Frame)) {
				return (Frame) parent;
			}
			parent = parent.getParent();
		}
		return null;
	}

	@Override
	public void close() throws IOException {
		audioSource.close();
		backgroundTasks.shutdown();
	}

	public static void main(String[] args) throws LineUnavailableException, IOException {
		GraphicsEnvironment env = GraphicsEnvironment.
			getLocalGraphicsEnvironment();
		GraphicsDevice device = env.getDefaultScreenDevice();
		DisplayMode preferred = new DisplayMode(800,600,32, DisplayMode.REFRESH_RATE_UNKNOWN);
		Dimension resolution = new Dimension(preferred.getWidth(), preferred.getHeight());

		DisplayMode nativeMode = device.getDisplayMode();

		GraphicsConfiguration gc = device.getDefaultConfiguration();
		Frame mainFrame = new Frame(gc);
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
		}
		mainFrame.createBufferStrategy(5);
		BufferStrategy bufferStrategy = mainFrame.getBufferStrategy();      //set frame visibility true
		JCthugha cthugha = new JCthugha(bufferStrategy);
		ScheduledExecutorService executorService = Executors.newScheduledThreadPool(3);
		mainFrame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				executorService.shutdown();
				mainFrame.dispose();
				try {
					cthugha.close();
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		});

		// initialise cthugha
		cthugha.init(resolution);

		executorService.scheduleAtFixedRate(cthugha, 100, 1000/60, TimeUnit.MILLISECONDS);
	}
}
