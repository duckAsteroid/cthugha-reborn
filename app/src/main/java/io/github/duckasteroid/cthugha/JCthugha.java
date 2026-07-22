package io.github.duckasteroid.cthugha;


import com.asteroid.duck.opengl.util.audio.analysis.BeatDetector;
import io.github.duckasteroid.cthugha.binding.BindingSystem;
import io.github.duckasteroid.cthugha.config.Config;
import io.github.duckasteroid.cthugha.display.AudioSourceNode;
import io.github.duckasteroid.cthugha.display.phase.FlashPhase;
import io.github.duckasteroid.cthugha.display.phase.NotifPhase;
import io.github.duckasteroid.cthugha.display.phase.QuotePhase;
import io.github.duckasteroid.cthugha.display.phase.RenderPhase;
import io.github.duckasteroid.cthugha.display.phase.WavePhase;
import io.github.duckasteroid.cthugha.display.wave.OscilloscopeModel;
import io.github.duckasteroid.cthugha.display.wave.RadialWaveModel;
import io.github.duckasteroid.cthugha.display.wave.WaveSystem;
import io.github.duckasteroid.cthugha.map.MapFileReader;
import io.github.duckasteroid.cthugha.map.PaletteMap;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import com.asteroid.duck.opengl.util.stats.Stats;
import com.asteroid.duck.opengl.util.stats.StatsFactory;
import io.github.duckasteroid.cthugha.quote.Constants;
import io.github.duckasteroid.cthugha.quote.Quote;
import io.github.duckasteroid.cthugha.quote.RandomQuoteSource;
import io.github.duckasteroid.cthugha.params.DynamicChildList;
import io.github.duckasteroid.cthugha.screenconfig.CurrentStatePersister;
import io.github.duckasteroid.cthugha.screenconfig.CurrentStateStore;
import io.github.duckasteroid.cthugha.screenconfig.ScreenConfigStore;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JCthugha extends ParamNode implements Closeable {

	private static final Logger LOG = LoggerFactory.getLogger(JCthugha.class);

	private static final Duration QUOTE_DURATION = Config.singleton().getConfigAs(
		Constants.SECTION, Constants.KEY_DURATION, "PT10S", Duration::parse);

	/** How often the "current" state is flushed to disk; see {@link CurrentStatePersister}. */
	private static final Duration CURRENT_STATE_WRITE_INTERVAL = Config.singleton().getConfigAs(
		"session", "current_state_interval", "PT5S", Duration::parse);

	/** Whether transient notifications are shown; auto-persisted across sessions in state.ini. */
	public final BooleanParameter notifications = new BooleanParameter("Notifications",
			Config.state().getConfigAs("display", "notifications", "true", Boolean::parseBoolean));

	/** Dynamic list of wave visualisation instances (replaces the old fixed one-of-each-type fields). */
	public WaveSystem waveSystem = new WaveSystem();
	public BindingSystem bindings = new BindingSystem();
	public AudioSourceNode audioSource = new AudioSourceNode();
	public TabStore tabStore = new TabStore(java.nio.file.Paths.get("tabs"));
	public GeneratorRegistry translateSource = new GeneratorRegistry(tabStore);
	public final ScreenConfigStore screenConfigStore;
	/** The single, unnamed "current" state, continuously persisted and restored on launch — see issue #3. */
	public final CurrentStateStore currentStateStore;
	private final CurrentStatePersister currentStatePersister;
	public FlashPhase flashPhase = new FlashPhase();
	public final QuotePhase quotePhase = new QuotePhase(this);
	public final WavePhase wavePhase = new WavePhase(this);

	public PaletteMap paletteMap;
	public int bufferWidth;
	public int bufferHeight;

	/** Set by {@link io.github.duckasteroid.cthugha.display.phase.WavePhase} once its audio
	 * pipeline is initialised; read by binding scripts via {@link
	 * io.github.duckasteroid.cthugha.binding.ScriptHelpers#setContext}. */
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
		this(Paths.get("configs"));
	}

	/**
	 * Test/advanced-use constructor allowing the directory that holds both named {@link
	 * ScreenConfigStore} configs and the persisted {@link CurrentStateStore} "current" state to be
	 * overridden — e.g. to a JUnit {@code @TempDir} so tests don't touch the real working
	 * directory's {@code configs/}. Production code should use the no-arg constructor.
	 */
	public JCthugha(Path configsRoot) {
		super("JCthugha");
		screenConfigStore = new ScreenConfigStore(configsRoot);
		currentStateStore = new CurrentStateStore(configsRoot);
		currentStatePersister = new CurrentStatePersister(currentStateStore, this, CURRENT_STATE_WRITE_INTERVAL);

		notifications.addChangeListener(() ->
				Config.state().setConfig("display", "notifications", String.valueOf(notifications.value)));

		// Deliberately no default wave/binding seeding here (see #resetToDefaults): whether a
		// fresh session gets deterministic defaults or the persisted "current" state is decided
		// once, by the caller, after the full tree exists — see CthughaWindow#init.
	}

	public void init(Dimension dims, Random rng) throws IOException {
		this.rng = rng;
		bufferWidth = dims.width;
		bufferHeight = dims.height;
		translate = new TabBuffer(dims);
		// Deterministic default: generate using the current generator's own (default) param
		// values rather than randomising them first (translateSource.generate() would). A fresh
		// session's starting distortion is then reproducible instead of "a bit random" on every
		// launch (see issue #3's investigation) -- CthughaWindow#init overrides this afterwards
		// with either the persisted "current" state or the user's own live choice.
		translate.fill(translateSource.generateCurrent(dims.width, dims.height, rng), rng);
		Path currentWorkingDir = Paths.get("").toAbsolutePath();
		System.out.println(currentWorkingDir.normalize().toString());
		Path maps = Paths.get("maps");
		reader = new MapFileReader(maps);
		reader.refreshPreviews();
		paletteMap = reader.first();
	}

	/**
	 * Resets Wave and Bindings to the app's fresh-session defaults: exactly one instance of each
	 * wave type (Oscilloscope enabled, the other three off, matching the pre-dynamic-list
	 * fixed-field defaults) plus two gentle rotation animations on the Oscilloscope and Radial
	 * Wave instances. Must be called after the full param tree is wired (i.e. after {@link
	 * io.github.duckasteroid.cthugha.ActionTreeBuilder#build}), since binding targets are
	 * captured via {@link io.github.duckasteroid.cthugha.params.ParamNode#getFullPath()} —
	 * calling it any earlier, while targets are still parentless, would capture an empty path.
	 *
	 * <p>Used two ways (see issue #3): automatically, once, on a genuine first-ever launch (no
	 * persisted "current" state yet to restore instead — see {@link #currentStateStore}); and as
	 * an explicit, user-invoked "Reset to Defaults" action at any later time. It is <em>not</em>
	 * called on every launch — that was the source of the "random-looking", unwanted default
	 * animations issue #3 reported.</p>
	 */
	public void resetToDefaults() {
		bindings.recreate(List.of()); // drop bindings first: they may target the old wave instances
		waveSystem.recreate(List.of(
				waveSpec(WaveSystem.WaveType.OSCILLOSCOPE),
				waveSpec(WaveSystem.WaveType.RADIAL_WAVE),
				waveSpec(WaveSystem.WaveType.SPECTRUM),
				waveSpec(WaveSystem.WaveType.RADIAL_SPECTRUM)));
		List<ParamNode> instances = waveSystem.instances();
		OscilloscopeModel osc = (OscilloscopeModel) instances.get(0);
		RadialWaveModel radial = (RadialWaveModel) instances.get(1);
		bindings.addContinuous("osc rotation",    osc.transform.rotate,    "sine(0.05)");
		bindings.addContinuous("radial rotation", radial.transform.rotate, "sine(0.07)");
	}

	private static DynamicChildList.ChildSpec waveSpec(WaveSystem.WaveType type) {
		return new DynamicChildList.ChildSpec(type.label() + " 1", type.name(), Map.of());
	}

	/**
	 * Loads the persisted "current" state if one exists, otherwise falls back to {@link
	 * #resetToDefaults()} (a genuine first-ever launch). Must be called after the full param tree
	 * is wired — see {@link #resetToDefaults()}. Starts the periodic background writer either way,
	 * so the newly-established state (restored or default) starts getting persisted immediately.
	 */
	public void restoreCurrentStateOrDefaults() {
		boolean restored = false;
		try {
			restored = currentStateStore.loadIfPresent(this);
		} catch (IOException e) {
			LOG.error("Failed to load persisted current state; falling back to defaults", e);
		}
		if (!restored) {
			resetToDefaults();
		}
		currentStatePersister.start();
	}

	/**
	 * Best-effort immediate write of the "current" state snapshot. Safe to call from any thread
	 * (e.g. a JVM shutdown hook, as a backstop for termination paths that skip the normal {@link
	 * #close()}), any number of times, and even before {@link #restoreCurrentStateOrDefaults()}
	 * has run; never throws.
	 */
	public void flushCurrentStateNow() {
		currentStatePersister.flushNow();
	}

	public synchronized Duration doRenderCPU() {
		final Instant start = Instant.now();
		try {
			frameRate.ping();
			bindings.tick();
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
		// Stops the periodic writer and performs one final synchronous flush, so a clean exit
		// never loses live edits made since the last periodic write (see issue #3).
		currentStatePersister.stop();
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
