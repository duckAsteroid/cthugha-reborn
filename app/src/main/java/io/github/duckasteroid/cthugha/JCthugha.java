package io.github.duckasteroid.cthugha;


import io.github.duckasteroid.cthugha.animation.AnimationSystem;
import io.github.duckasteroid.cthugha.config.Config;
import io.github.duckasteroid.cthugha.display.wave.OscilloscopeModel;
import io.github.duckasteroid.cthugha.display.wave.RadialSpectrumModel;
import io.github.duckasteroid.cthugha.display.wave.RadialWaveModel;
import io.github.duckasteroid.cthugha.display.wave.SpectrumModel;
import io.github.duckasteroid.cthugha.map.MapFileReader;
import io.github.duckasteroid.cthugha.map.PaletteMap;
import io.github.duckasteroid.cthugha.params.AbstractNode;
import com.asteroid.duck.opengl.util.stats.Stats;
import com.asteroid.duck.opengl.util.stats.StatsFactory;
import io.github.duckasteroid.cthugha.strings.Constants;
import io.github.duckasteroid.cthugha.strings.Quote;
import io.github.duckasteroid.cthugha.strings.RandomStringSource;
import io.github.duckasteroid.cthugha.tab.GeneratorRegistry;
import io.github.duckasteroid.cthugha.tab.TabBuffer;
import io.github.duckasteroid.cthugha.tab.TabStore;
import java.awt.Dimension;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JCthugha extends AbstractNode implements Closeable {

	private static final Logger LOG = LoggerFactory.getLogger(JCthugha.class);

	private static final Duration QUOTE_DURATION = Config.singleton().getConfigAs(
		Constants.SECTION, Constants.KEY_DURATION, "PT10S", Duration::parse);

	private boolean notify = true;

	public OscilloscopeModel oscilloscope = new OscilloscopeModel();
	public RadialWaveModel radialWave = new RadialWaveModel();
	public SpectrumModel spectrum = new SpectrumModel();
	public RadialSpectrumModel radialSpectrum = new RadialSpectrumModel();
	public AnimationSystem animation = new AnimationSystem();
	public TabStore tabStore = new TabStore(java.nio.file.Paths.get("tabs"));
	public GeneratorRegistry translateSource = new GeneratorRegistry(tabStore);

	public PaletteMap paletteMap;
	public int bufferWidth;
	public int bufferHeight;

	public MapFileReader reader;

	TabBuffer translate;
	public Random rng;

	Stats frameRate = StatsFactory.deltaStats("frameRate");



	private final RandomStringSource stringSource = new RandomStringSource();
	private volatile String currentQuoteText = null;
	private Instant quoteExpiry = null;
	private volatile String pendingNotification = null;

	public JCthugha() {
		super("JCthugha");
	}

	public void init(Dimension dims, Random rng) throws IOException {
		this.rng = rng;
		bufferWidth = dims.width;
		bufferHeight = dims.height;
		translate = new TabBuffer(dims);
		translate.fill(translateSource.generate(dims.width, dims.height, rng), rng);
		Path currentWorkingDir = Paths.get("").toAbsolutePath();
		System.out.println(currentWorkingDir.normalize().toString());
		Path maps = Paths.get("maps");
		reader = new MapFileReader(maps);
		paletteMap = reader.random();

		animation.addBinding("osc rotation",    oscilloscope.transform.rotate, 0.05);
		animation.addBinding("radial rotation", radialWave.transform.rotate, 0.07);
	}

	public synchronized Duration doRenderCPU() {
		final Instant start = Instant.now();
		try {
			frameRate.ping();
			animation.tick();
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

	public ByteBuffer getTranslateBuffer() {
		return translate.getBuffer();
	}

	public TabBuffer getTabBuffer() {
		return translate;
	}

	/** Replaces the active translation buffer (e.g. when loading a saved preset). */
	public void loadTabBuffer(TabBuffer t) {
		this.translate = t;
	}

	/** Randomises the current generator's params and regenerates the translation map. */
	public void newTranslation(Random rng) {
		translate.fill(translateSource.generate(bufferWidth, bufferHeight, rng), rng);
		notify(translateSource.getLastGenerated());
	}

	/** Regenerates the translation map using the current generator's current param values. */
	public void regenerateTranslation() {
		translate.fill(translateSource.generateCurrent(bufferWidth, bufferHeight, rng), rng);
		notify(translateSource.getLastGenerated());
	}

	public void loadPalette(PaletteMap map) {
		paletteMap = map;
		notify(map.getName());
	}

	public void newPalette() {
		try {
			loadPalette(reader.random());
		} catch (IOException ioe) {
			LOG.error("Error loading palette", ioe);
		}
	}

	public void toggleDebug() {
		notify("debug toggled (no-op in OpenGL mode)");
	}

	@Override
	public void close() throws IOException {
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

}
