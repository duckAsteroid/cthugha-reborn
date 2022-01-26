package io.github.duckasteroid.cthugha;

import io.github.duckasteroid.cthugha.audio.SampledAudioSource;
import io.github.duckasteroid.cthugha.flame.Flame;
import io.github.duckasteroid.cthugha.map.MapFileReader;
import io.github.duckasteroid.cthugha.tab.Hurricane;
import io.github.duckasteroid.cthugha.tab.Spiral;
import io.github.duckasteroid.cthugha.wave.SimpleWave;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Panel;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
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

	TimeStatistics timeStatistics = new TimeStatistics();

	public JCthugha() throws LineUnavailableException {
	}

	public void init() throws IOException {
		IndexColorModel icm;

		sizeChanged();

		Path maps = Paths.get("maps");
		reader = new MapFileReader(maps);


		//translate = new Translate(dimension, new Smoke(40,40).generate(dimension));
		icm = reader.random();
		source = new MemoryImageSource( buffer.width, buffer.height, icm, buffer.pixels, 0, buffer.width);
		source.setAnimated(true);
		img = createImage( source );
		prepareImage( img, this );


		addKeyListener(new KeyAdapter() {
			@Override
			public void keyTyped(KeyEvent e) {
				if (e.getKeyChar() == 'p' || e.getKeyChar() == 'P') {
					try {
						source.newPixels(buffer.pixels, reader.random(), 0, buffer.width);
					}
					catch (IOException ex) {

					}
				}
				else if(e.getKeyChar() == 's' || e.getKeyChar() == 'S') {
					System.out.println(timeStatistics);
				}
			}
		});
	}

	private void sizeChanged() {
		Dimension dims = getSize();

		sound = new int[dims.width];

		buffer = new ScreenBuffer(dims.width, dims.height);
		shadow = new ScreenBuffer(dims.width, dims.height);

		translate = new Translate(dims, new Spiral().numSpirals(0).deltaA(0.0000003).deltaR(0.001).generate(dims));
		//translate = new Translate(dims, new Hurricane().generate(dims));
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
		g.drawImage(img, 0, 0, this);
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
	}

	public static void main(String[] args) throws LineUnavailableException, IOException {
		final Frame f = new Frame();
		final JCthugha jCthugha = new JCthugha();
		ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(3);
		jCthugha.setBounds(0, 0, 2048, 2048);
		f.add(jCthugha);         //adding a new Button.
		f.setSize(2048, 2048);        //setting size.
		f.setTitle("Java Cthugha");  //setting title.
		//f.setLayout(null);   //set default layout for frame.
		f.setVisible(true);           //set frame visibility true
		f.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				executorService.shutdown();
				f.dispose();
			}
		});
		jCthugha.init();

		executorService.scheduleAtFixedRate(jCthugha, 0, 1000/60, TimeUnit.MILLISECONDS);
	}
}
