package io.github.duckasteroid.cthugha;


import com.asteroid.duck.opengl.util.audio.AudioDataSource;
import com.asteroid.duck.opengl.util.audio.LineAcquirer;
import io.github.duckasteroid.cthugha.audio.AudioBuffer;
import io.github.duckasteroid.cthugha.audio.AudioSample;
import io.github.duckasteroid.cthugha.audio.Channel;
import io.github.duckasteroid.cthugha.audio.dsp.FastFourierTransform;
import io.github.duckasteroid.cthugha.config.Config;
import io.github.duckasteroid.cthugha.display.DisplayResolution;
import io.github.duckasteroid.cthugha.display.ScreenBuffer;
import io.github.duckasteroid.cthugha.img.RandomImageSource;
import io.github.duckasteroid.cthugha.map.MapFileReader;
import io.github.duckasteroid.cthugha.notify.NotificationRenderer;
import io.github.duckasteroid.cthugha.animation.AnimatorPool;
import io.github.duckasteroid.cthugha.config.Config;
import io.github.duckasteroid.cthugha.params.AbstractNode;
import io.github.duckasteroid.cthugha.stats.Stats;
import io.github.duckasteroid.cthugha.stats.StatsFactory;
import io.github.duckasteroid.cthugha.strings.Constants;
import io.github.duckasteroid.cthugha.strings.Quote;
import io.github.duckasteroid.cthugha.strings.RandomStringSource;
import io.github.duckasteroid.cthugha.tab.RandomTranslateSource;
import io.github.duckasteroid.cthugha.tab.Translate;
import io.github.duckasteroid.cthugha.wave.RadialWave;
import io.github.duckasteroid.cthugha.wave.SpeckleWave;
import io.github.duckasteroid.cthugha.wave.SpectraBars;
import io.github.duckasteroid.cthugha.wave.Wave;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JCthugha extends AbstractNode implements Closeable {

	private static final Logger LOG = LoggerFactory.getLogger(JCthugha.class);

	private static final Duration QUOTE_DURATION = Config.singleton().getConfigAs(
		Constants.SECTION, Constants.KEY_DURATION, "PT10S", Duration::parse);

	private final AnimatorPool animatorPool = new AnimatorPool();

	private boolean notify = true;

	private AudioDataSource audioDataSource;
	private AudioBuffer audioBuffer;

	public ScreenBuffer buffer;

	MapFileReader reader;

	Translate translate;

	final Instant started = Instant.now();

	final Wave wave2 = new RadialWave();
	final Wave speckles = new SpeckleWave();
	boolean doSpeckles = false;

	final Wave fft = new SpectraBars(new FastFourierTransform(512, LineAcquirer.IDEAL, Channel.MONO_AVG));
	boolean doFFT = true;

	Stats frameRate = StatsFactory.deltaStats("frameRate");

	RandomTranslateSource translateSource = new RandomTranslateSource();

	RandomImageSource imageSource = new RandomImageSource(Paths.get("pcx"));

	private final RandomStringSource stringSource = new RandomStringSource();
	private volatile String currentQuoteText = null;
	private Instant quoteExpiry = null;
	private volatile String pendingNotification = null;

	public JCthugha() {
	}

	public void setAudioDataSource(AudioDataSource ds) {
		this.audioDataSource = ds;
	}

	public void init(Dimension dims) throws IOException {

		buffer = new ScreenBuffer(dims.width, dims.height);
		translate = new Translate(dims, translateSource.generate(buffer, true));
		Path currentWorkingDir = Paths.get("").toAbsolutePath();
		System.out.println(currentWorkingDir.normalize().toString());
		Path maps = Paths.get("maps");
		reader = new MapFileReader(maps);
		buffer.paletteMap = reader.random();
		audioBuffer = new AudioBuffer(LineAcquirer.IDEAL, Duration.ofMillis(200));
	}

	public synchronized Duration doRenderCPU() {
		final Instant start = Instant.now();
		try {
			frameRate.ping();
			animatorPool.doAnimation(Duration.between(started, start));
		AudioSample audioSample = (audioDataSource != null)
				? audioBuffer.readFrom(audioDataSource, buffer.width)
				: AudioSample.EMPTY;
			wave2.wave(audioSample, buffer);
			if (doSpeckles) speckles.wave(audioSample, buffer);
			if (doFFT) fft.wave(audioSample, buffer);
		} catch (Throwable t) {
			LOG.error("Processing main loop", t);
		}
		return Duration.between(start, Instant.now());
	}

	public void notify(String message) {
		if (notify) {
			LOG.info("notify: {}", message);
			pendingNotification = message;
		}
	}

	public String pollNotification() {
		String n = pendingNotification;
		pendingNotification = null;
		return n;
	}

	public String getCurrentQuote() {
		if (currentQuoteText != null && Instant.now().isBefore(quoteExpiry)) {
			return currentQuoteText;
		}
		currentQuoteText = null;
		return null;
	}

	public int[] getTranslateTable() {
		return translate.getTable();
	}

	public void newTranslation(boolean newMap) {
		translate.changeTable(translateSource.generate(buffer, newMap));
		notify(translateSource.getLastGenerated());
	}

	public void changeAmplitude(double ratio) {
		if (audioBuffer != null) {
			audioBuffer.setAmplification(audioBuffer.getAmplification() * ratio);
			notify("Amplitude = " + audioBuffer.getAmplification());
		}
	}

	public void newPalette() {
		try {
			buffer.paletteMap = reader.random();
			notify(buffer.paletteMap.getName());
		} catch (IOException ioe) {
			LOG.error("Error loading palette", ioe);
		}
	}

	public void toggleDebug() {
		notify("debug toggled (no-op in OpenGL mode)");
	}

	public void toggleSpeckle() {
		this.doSpeckles = !doSpeckles;
		notify("speckles=" + doSpeckles);
	}

	public void rotate(double degrees) {
		notify("rotate=" + degrees + " (wave rotation handled by GL renderer)");
	}

	public void autoRotateWave(double amount) {
	}

	@Override
	public void close() throws IOException {
		// audio lifecycle managed by CthughaWindow via LineAcquirer
	}

	public void showQuote() {
		Quote q = stringSource.nextQuote();
		currentQuoteText = q.quote() + "\n  — " + q.author();
		quoteExpiry = Instant.now().plus(QUOTE_DURATION);
	}

	public void toggleNotifications() {
		notify = !notify;
		LOG.info("notifications={}", notify);
	}

	public void toggleAudioSource() {
		// audio source cycling is handled by CthughaWindow via LineAcquirer
	}

	public void flashImage() {
		try {
			BufferedImage flash = imageSource.nextImage();
			Graphics2D graphics = buffer.getBufferedImageView().createGraphics();
			graphics.drawImage(flash, 0, 0, buffer.width, buffer.height, null);
			graphics.dispose();
		} catch (IOException e) {
			LOG.error("Error flash image", e);
		}
	}
}
