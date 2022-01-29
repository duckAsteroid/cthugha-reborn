package io.github.duckasteroid.cthugha;

import io.github.duckasteroid.cthugha.audio.SampledAudioSource;
import io.github.duckasteroid.cthugha.flame.Flame;
import io.github.duckasteroid.cthugha.map.MapFileReader;
import io.github.duckasteroid.cthugha.stats.Stats;
import io.github.duckasteroid.cthugha.stats.StatsFactory;
import io.github.duckasteroid.cthugha.tab.RandomTranslateSource;
import io.github.duckasteroid.cthugha.tab.Spiral;
import io.github.duckasteroid.cthugha.tab.Translate;
import io.github.duckasteroid.cthugha.wave.SimpleWave;
import java.awt.Dimension;
import java.awt.DisplayMode;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Panel;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.ImageObserver;
import java.awt.image.IndexColorModel;
import java.awt.image.MemoryImageSource;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.LineUnavailableException;

public class JCthugha extends Panel implements Runnable, ImageObserver, Closeable {

	final SampledAudioSource audioSource = new SampledAudioSource();
	int [] sound;

	ScreenBuffer buffer;
	ScreenBuffer shadow;

	MapFileReader reader;

	Image img;
	MemoryImageSource source;

	Translate translate;

	final Flame flame = new Flame();

	final SimpleWave wave = new SimpleWave().wave(10);

	Stats timeStatistics = StatsFactory.deltaStats("frameRate");

	RandomTranslateSource translateSource = new RandomTranslateSource();

	ExecutorService backgroundTasks = Executors.newFixedThreadPool(2);

	public JCthugha() throws LineUnavailableException {
	}

	public void init(Dimension bufferSize) throws IOException {

		IndexColorModel icm;

		sizeChanged(bufferSize);

		Path maps = Paths.get("maps");
		reader = new MapFileReader(maps);


		icm = reader.random();
		source = new MemoryImageSource( buffer.width, buffer.height, icm, buffer.pixels, 0, buffer.width);
		source.setAnimated(true);
		img = createImage( source );
		prepareImage( img, this );

		addKeyListener(new KeyAdapter() {
			@Override
			public void keyTyped(KeyEvent e) {
				if (e.getKeyChar() == 'p' || e.getKeyChar() == 'P') {
					backgroundTasks.submit(() -> {
						try {
							source.newPixels(buffer.pixels, reader.random(), 0, buffer.width);
						} catch (IOException ex) {

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
			}
		});
	}

	private void sizeChanged(Dimension dims) {
		sound = new int[dims.width];

		buffer = new ScreenBuffer(dims.width, dims.height);
		shadow = new ScreenBuffer(dims.width, dims.height);

		translate = new Translate(dims, translateSource.generate(dims, true));
	}


	public boolean imageUpdate(Image img, int flags, int x, int y, int w, int h) {
		if( ( flags & (ImageObserver.FRAMEBITS | ImageObserver.ALLBITS) ) != 0 ) {
			repaint();
		}
		if( ( flags & (ImageObserver.ERROR | ImageObserver.ABORT) ) != 0 ) {
			return false;
		}
		return true;
	}

	public void paint(Graphics g) {
		update(g);
	}

	public void update(Graphics g) {
		Dimension size = getSize();
		g.drawImage(img, 0, 0, size.width, size.height, this);
		timeStatistics.ping();
	}

	public synchronized void run() {

			// get sound
			audioSource.sample(sound, buffer.width, buffer.height);

			// translate
			translate.transform(shadow.pixels, buffer.pixels);

			// flame
			flame.flame(buffer);

			//wave
			wave.wave(sound, buffer);

			source.newPixels();
			// copy
			shadow.copy(buffer);

	}


	@Override
	public void close() throws IOException {
		audioSource.close();
		backgroundTasks.shutdown();
	}

	public static void main(String[] args) throws LineUnavailableException, IOException {
		DisplayMode displayMode =
			GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode();
		Dimension screenSize =  new Dimension(displayMode.getWidth(), displayMode.getHeight());
		int fract = 3;
		Dimension cthughaBufferSize = new Dimension(screenSize.width / fract, screenSize.height / fract);
		final Frame f = new Frame();
		final JCthugha jCthugha = new JCthugha();
		ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(3);
		jCthugha.setBounds(0, 0, screenSize.width, screenSize.height);
		f.add(jCthugha);         //adding a new Button.
		f.setSize(screenSize.width, screenSize.height);        //setting size.
		//f.setTitle("Java Cthugha");  //setting title.
		//f.setLayout(null);   //set default layout for frame.
		f.setUndecorated(true);
		f.setResizable(false);
		f.setVisible(true);           //set frame visibility true
		f.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				executorService.shutdown();
				f.dispose();
				try {
					jCthugha.close();
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		});

		// initialise cthugha
		jCthugha.init(cthughaBufferSize);

		executorService.scheduleAtFixedRate(jCthugha, 100, 1000/60, TimeUnit.MILLISECONDS);
	}
}
