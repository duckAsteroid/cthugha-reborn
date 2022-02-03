package io.github.duckasteroid.cthugha;

import static io.github.duckasteroid.cthugha.stats.Statistics.to2DP;

import io.github.duckasteroid.cthugha.audio.AudioBuffer;
import io.github.duckasteroid.cthugha.audio.SampledAudioSource;
import io.github.duckasteroid.cthugha.flame.Flame;
import io.github.duckasteroid.cthugha.map.MapFileReader;
import io.github.duckasteroid.cthugha.stats.Stats;
import io.github.duckasteroid.cthugha.stats.StatsFactory;
import io.github.duckasteroid.cthugha.tab.RandomTranslateSource;
import io.github.duckasteroid.cthugha.tab.Translate;
import io.github.duckasteroid.cthugha.wave.SimpleWave;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.DisplayMode;
import java.awt.Font;
import java.awt.Frame;
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
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.LineUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JCthugha implements Runnable, Closeable {

	private static final Logger LOG = LoggerFactory.getLogger(JCthugha.class);

	final SampledAudioSource audioSource = new SampledAudioSource();
	int [] sound;

	ScreenBuffer buffer;

	MapFileReader reader;

	Translate translate;

	final Flame flame = new Flame();

	final SimpleWave wave = new SimpleWave().wave(10);

	Stats timeStatistics = StatsFactory.deltaStats("frameRate");

	RandomTranslateSource translateSource = new RandomTranslateSource();

	BufferStrategy bufferStrategy;
	private BufferedImage screenImage;
	private Dimension windowDimensions;

	private boolean debug = true;
	private Color debugColor = Color.GREEN;
	private Font debugFont = new Font("Courier New", Font.BOLD, 24);
	private long nanoTime = System.nanoTime();
	private static final long nanosecond = Duration.ofSeconds(1).toNanos();

	public JCthugha() throws LineUnavailableException {
	}

	public void init(Dimension dims, BufferStrategy bufferStrategy, BufferedImage screenImage, Dimension windowDimensions) throws IOException {
		this.bufferStrategy = bufferStrategy;
		this.screenImage = screenImage;
		this.windowDimensions = windowDimensions;
		sound = new int[dims.width];
		buffer = new ScreenBuffer(dims.width, dims.height);
		translate = new Translate(dims, translateSource.generate(dims, true));
		Path maps = Paths.get("maps");
		reader = new MapFileReader(maps);
		buffer.paletteMap = reader.random();
	}

	public synchronized void run() {
			try {
				timeStatistics.ping();
				// translate
				translate.transform(buffer.pixels, buffer.pixels);

				// flame
				flame.flame(buffer);

				// get sound
				AudioBuffer.AudioSample audioSample = audioSource.sample(buffer.width);

				// wave
				wave.wave(audioSample, buffer);

				// render the buffer onto the screen ready image
				buffer.render(screenImage.getRaster());

				// draw onto back buffer
				Graphics g2d = bufferStrategy.getDrawGraphics();
				g2d.drawImage(screenImage, 0,0, windowDimensions.width, windowDimensions.height, null);
				if (debug) {
					g2d.setColor(debugColor);
					g2d.setFont(debugFont);
					long now = System.nanoTime();
					long elapsed = now - nanoTime;
					nanoTime = now;
					double hz = ((double) nanosecond / (double) elapsed);
					int y = 50;
					g2d.drawString(to2DP(hz) +" FPS", 10, y);
					int fontHeight = g2d.getFontMetrics().getHeight();
					y+=fontHeight;
					g2d.drawString(buffer.paletteMap.getName(), 10, y);
					y+=fontHeight;
					g2d.drawImage(buffer.paletteMap.getPaletteImage(), 0, y, windowDimensions.width, 10, null);
				}
				g2d.dispose();

				// flip
				if( !bufferStrategy.contentsLost() )
					bufferStrategy.show();
			}
			catch(Throwable t) {
				LOG.error("Processing main loop", t);
			}
	}

	public void newTranslation(boolean newMap) {
		translate.changeTable(translateSource.generate(buffer.getDimensions(), newMap));
	}

	public void newPalette() {
		try {
			buffer.paletteMap = reader.random();
		}
		catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}

	public void toggleDebug() {
		this.debug = !debug;
	}

	@Override
	public void close() throws IOException {
		audioSource.close();
	}

	public static void main(String[] args) throws LineUnavailableException, IOException {
		GraphicsEnvironment localGraphicsEnvironment =
			GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice gd = localGraphicsEnvironment.getDefaultScreenDevice();
		DisplayMode displayMode =	gd.getDisplayMode();
		Dimension screenSize = new Dimension(1024,768); //new Dimension(displayMode.getWidth(), displayMode.getHeight());
		System.out.println(screenSize);
		int fract = 1;
		Dimension cthughaBufferSize = new Dimension(screenSize.width / fract, screenSize.height / fract);
		final Frame f = new Frame();
		final JCthugha jCthugha = new JCthugha();
		ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(3);
		//jCthugha.setBounds(0, 0, screenSize.width, screenSize.height);
		//f.add(jCthugha);         //adding a new Button.
		f.setSize(screenSize.width, screenSize.height);        //setting size.
		//f.setTitle("Java Cthugha");  //setting title.
		//f.setLayout(null);   //set default layout for frame.
		//f.setUndecorated(true);
		f.setIgnoreRepaint(true);
		//f.setResizable(false);
		f.setVisible(true);           //set frame visibility true
		f.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				executorService.shutdown();
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
				if(e.getKeyChar() == 's') {
					System.out.println(StatsFactory.getStatisticsSummary());
				}
				else if (e.getKeyChar() == 't' || e.getKeyChar() == 'T') {
					jCthugha.newTranslation(e.isShiftDown());
				}
				else if (e.getKeyChar() == 'p') {
					jCthugha.newPalette();
				}
			}
		});

		f.createBufferStrategy(3);

		// initialise cthugha
		GraphicsConfiguration graphicsConfiguration = gd.getDefaultConfiguration();
		BufferedImage screenCompatibleImage =
			graphicsConfiguration.createCompatibleImage(cthughaBufferSize.width,
				cthughaBufferSize.height);
		jCthugha.init(cthughaBufferSize, f.getBufferStrategy(), screenCompatibleImage, screenSize);

		executorService.scheduleAtFixedRate(jCthugha, 100, 1000/70, TimeUnit.MILLISECONDS);
	}
}
