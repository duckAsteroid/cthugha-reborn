package io.github.duckasteroid.cthugha;


import com.asteroid.duck.opengl.util.audio.analysis.BeatDetector;
import io.github.duckasteroid.cthugha.animation.AnimationSystem;
import io.github.duckasteroid.cthugha.config.Config;
import io.github.duckasteroid.cthugha.display.AudioSourceNode;
import io.github.duckasteroid.cthugha.display.phase.FlashPhase;
import io.github.duckasteroid.cthugha.display.phase.NotifPhase;
import io.github.duckasteroid.cthugha.display.phase.QuotePhase;
import io.github.duckasteroid.cthugha.display.phase.RenderPhase;
import io.github.duckasteroid.cthugha.display.phase.WavePhase;
import io.github.duckasteroid.cthugha.display.wave.OscilloscopeModel;
import io.github.duckasteroid.cthugha.display.wave.RadialSpectrumModel;
import io.github.duckasteroid.cthugha.display.wave.RadialWaveModel;
import io.github.duckasteroid.cthugha.display.wave.SpectrumModel;
import io.github.duckasteroid.cthugha.map.MapFileReader;
import io.github.duckasteroid.cthugha.map.PaletteMap;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import com.asteroid.duck.opengl.util.stats.Stats;
import com.asteroid.duck.opengl.util.stats.StatsFactory;
import io.github.duckasteroid.cthugha.quote.Constants;
import io.github.duckasteroid.cthugha.quote.Quote;
import io.github.duckasteroid.cthugha.quote.RandomQuoteSource;
import io.github.duckasteroid.cthugha.screenconfig.ScreenConfigStore;
import io.github.duckasteroid.cthugha.tab.GeneratorRegistry;
import io.github.duckasteroid.cthugha.tab.TabBuffer;
import io.github.duckasteroid.cthugha.tab.TabStore;
import io.github.duckasteroid.cthugha.trigger.TriggerSystem;
import java.awt.Dimension;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JCthugha extends ParamNode implements Closeable {

	private static final Logger LOG = LoggerFactory.getLogger(JCthugha.class);

	private static final Duration QUOTE_DURATION = Config.singleton().getConfigAs(
		Constants.SECTION, Constants.KEY_DURATION, "PT10S", Duration::parse);

	/** Whether transient notifications are shown; auto-persisted across sessions in state.ini. */
	public final BooleanParameter notifications = new BooleanParameter("Notifications",
			Config.state().getConfigAs("display", "notifications", "true", Boolean::parseBoolean));

	public OscilloscopeModel oscilloscope = new OscilloscopeModel();
	public RadialWaveModel radialWave = new RadialWaveModel();
	public SpectrumModel spectrum = new SpectrumModel();
	public RadialSpectrumModel radialSpectrum = new RadialSpectrumModel();
	public AnimationSystem animation = new AnimationSystem();
	public TriggerSystem triggers = new TriggerSystem();
	public AudioSourceNode audioSource = new AudioSourceNode();
	public TabStore tabStore = new TabStore(java.nio.file.Paths.get("tabs"));
	public GeneratorRegistry translateSource = new GeneratorRegistry(tabStore);
	public ScreenConfigStore screenConfigStore = new ScreenConfigStore(java.nio.file.Paths.get("configs"));
	public FlashPhase flashPhase = new FlashPhase();
	public final QuotePhase quotePhase = new QuotePhase(this);
	public final WavePhase wavePhase = new WavePhase(this);

	public PaletteMap paletteMap;
	public int bufferWidth;
	public int bufferHeight;

	/** Set by {@link io.github.duckasteroid.cthugha.display.phase.WavePhase} once its audio
	 * pipeline is initialised; read by animation/trigger scripts via {@link
	 * io.github.duckasteroid.cthugha.animation.ScriptHelpers#setContext}. */
	public volatile BeatDetector beatDetector;

	public MapFileReader reader;

	TabBuffer translate;
	public Random rng;

	Stats frameRate = StatsFactory.deltaStats("frameRate");



	private final RandomQuoteSource quoteSource = new RandomQuoteSource();
	private volatile Quote currentQuote = null;
	private Instant quoteExpiry = null;
	private volatile String pendingNotification = null;

	public JCthugha() {
		super("JCthugha");
		notifications.addChangeListener(() ->
				Config.state().setConfig("display", "notifications", String.valueOf(notifications.value)));
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
		reader.refreshPreviews();
		paletteMap = reader.random();

		animation.addBinding("osc rotation",    oscilloscope.transform.rotate, "sine(0.05)");
		animation.addBinding("radial rotation", radialWave.transform.rotate,   "sine(0.07)");
	}

	public synchronized Duration doRenderCPU() {
		final Instant start = Instant.now();
		try {
			frameRate.ping();
			animation.tick();
			triggers.tick();
		} catch (Throwable t) {
			LOG.error("Processing main loop", t);
		}
		return Duration.between(start, Instant.now());
	}

	public void notify(String message) {
		if (notifications.value) {
			LOG.info("notify: {}", message);
			pendingNotification = message;
		}
	}

	public String pollNotification() {
		String n = pendingNotification;
		pendingNotification = null;
		return n;
	}

	public Quote getCurrentQuote() {
		if (currentQuote != null && Instant.now().isBefore(quoteExpiry)) {
			return currentQuote;
		}
		currentQuote = null;
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

	/** Computes a randomised translation into a fresh buffer (safe to call off the GL thread). */
	public TabBuffer computeNewTranslation() {
		TabBuffer newBuf = new TabBuffer(new Dimension(bufferWidth, bufferHeight));
		newBuf.fill(translateSource.generate(bufferWidth, bufferHeight, rng), rng);
		return newBuf;
	}

	/** Computes a regenerated translation into a fresh buffer (safe to call off the GL thread). */
	public TabBuffer computeRegeneratedTranslation() {
		TabBuffer newBuf = new TabBuffer(new Dimension(bufferWidth, bufferHeight));
		newBuf.fill(translateSource.generateCurrent(bufferWidth, bufferHeight, rng), rng);
		return newBuf;
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

	public List<RenderPhase> createPhases() {
		List<RenderPhase> list = new ArrayList<>();
		list.add(wavePhase);
		list.add(flashPhase);
		list.add(quotePhase);
		list.add(new NotifPhase(this));
		return list;
	}

	@Override
	public void close() throws IOException {
	}

	public void showQuote() {
		showQuote(quoteSource.nextQuote());
	}

	/** Shows a specific quote (e.g. picked from the remote UI's Quotes tab). */
	public void showQuote(Quote quote) {
		currentQuote = quote;
		quoteExpiry = Instant.now().plus(QUOTE_DURATION);
	}

	/** All quotes available for selection in the remote UI's Quotes tab. */
	public List<Quote> quotes() {
		return quoteSource.quotes();
	}

}
