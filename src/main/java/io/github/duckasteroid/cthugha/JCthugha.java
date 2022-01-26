package io.github.duckasteroid.cthugha;

import io.github.duckasteroid.cthugha.audio.SampledAudioSource;
import io.github.duckasteroid.cthugha.Brake;
import io.github.duckasteroid.cthugha.flame.Flame;
import io.github.duckasteroid.cthugha.map.MapFileReader;
import io.github.duckasteroid.cthugha.ScreenBuffer;
import io.github.duckasteroid.cthugha.TimeStatistics;
import io.github.duckasteroid.cthugha.Translate;
import io.github.duckasteroid.cthugha.wave.SimpleWave;
import io.github.duckasteroid.cthugha.tab.Hurricane;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.*;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Hashtable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.LineUnavailableException;

public class JCthugha implements Runnable {

	final SampledAudioSource audioSource = new SampledAudioSource();
	int [] sound;

	ScreenBuffer buffer;
	ScreenBuffer shadow;

	MapFileReader reader;

	int[] translate;

	final Flame flame = new Flame();

	final SimpleWave wave = new SimpleWave().wave(10);

	MemoryImageSource source;

	public JCthugha() throws LineUnavailableException {
	}

	public void init(ScreenManager screen) throws IOException {
		IndexColorModel icm;

		Path maps = Paths.get("maps");
		reader = new MapFileReader(maps);
		icm = reader.random();

		Dimension size = screen.getDimensions();

		buffer = new ScreenBuffer(size);
		shadow = new ScreenBuffer(size);
		source = new MemoryImageSource(screen.getWidth(), screen.getHeight(), icm, buffer.pixels, 0, size.width);
		source.addConsumer(new ImageConsumer() {
			@Override
			public void setDimensions(int width, int height) {
				
			}

			@Override
			public void setProperties(Hashtable<?, ?> props) {

			}

			@Override
			public void setColorModel(ColorModel model) {

			}

			@Override
			public void setHints(int hintflags) {

			}

			@Override
			public void setPixels(int x, int y, int w, int h, ColorModel model, byte[] pixels, int off,
			                      int scansize) {

			}

			@Override
			public void setPixels(int x, int y, int w, int h, ColorModel model, int[] pixels, int off,
			                      int scansize) {

			}

			@Override
			public void imageComplete(int status) {

			}
		});

	}

	private static final DisplayMode POSSIBLE_MODES[] = {
		new DisplayMode(800, 600, 32, 0),
		new DisplayMode(800, 600, 24, 0),
		new DisplayMode(800, 600, 16, 0),
		new DisplayMode(640, 480, 32, 0),
		new DisplayMode(640, 480, 24, 0),
		new DisplayMode(640, 480, 16, 0)
	};

	public static void main(String[] args) throws LineUnavailableException, IOException {
		JCthugha main = new JCthugha();

		final ScreenManager screen = new ScreenManager();
		try {
			DisplayMode displayMode =
				screen.findFirstCompatibleMode(POSSIBLE_MODES);
			screen.setFullScreen(displayMode);

			main.init(screen);
			final ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(3);
			executorService.scheduleAtFixedRate(main, 0, 1000 / 60, TimeUnit.MILLISECONDS);

		}
		finally {
			screen.restoreScreen();
		}
	}


	public void run() {
		// get sound
		audioSource.sample(sound, buffer.width, buffer.height);

		// translate
		for(int i =0 ; i < translate.length; i++) {
			buffer.pixels[i] = shadow.pixels[translate[i]];
		}
		// flame
		flame.flame(buffer);

		//wave
		wave.wave(sound, buffer);

		source.newPixels();
		// copy
		shadow.copy(buffer);
	}


}
