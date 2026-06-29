package io.github.duckasteroid.cthugha;


import io.github.duckasteroid.cthugha.config.Config;
import io.github.duckasteroid.cthugha.display.ScreenBuffer;
import io.github.duckasteroid.cthugha.map.MapFileReader;
import io.github.duckasteroid.cthugha.params.AbstractNode;
import com.asteroid.duck.opengl.util.stats.Stats;
import com.asteroid.duck.opengl.util.stats.StatsFactory;
import io.github.duckasteroid.cthugha.strings.Constants;
import io.github.duckasteroid.cthugha.strings.Quote;
import io.github.duckasteroid.cthugha.strings.RandomStringSource;
import io.github.duckasteroid.cthugha.tab.RandomTranslateSource;
import io.github.duckasteroid.cthugha.tab.Translate;
import java.awt.Dimension;
import java.io.Closeable;
import java.io.IOException;
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

	public ScreenBuffer buffer;

	MapFileReader reader;

	Translate translate;

	Stats frameRate = StatsFactory.deltaStats("frameRate");

	RandomTranslateSource translateSource = new RandomTranslateSource();

	private final RandomStringSource stringSource = new RandomStringSource();
	private volatile String currentQuoteText = null;
	private Instant quoteExpiry = null;
	private volatile String pendingNotification = null;

	public JCthugha() {
	}

	public void init(Dimension dims, Random rng) throws IOException {
		buffer = new ScreenBuffer(dims.width, dims.height);
		translate = new Translate(dims, translateSource.generate(dims.width, dims.height, true, rng));
		Path currentWorkingDir = Paths.get("").toAbsolutePath();
		System.out.println(currentWorkingDir.normalize().toString());
		Path maps = Paths.get("maps");
		reader = new MapFileReader(maps);
		buffer.paletteMap = reader.random();
	}

	public synchronized Duration doRenderCPU() {
		final Instant start = Instant.now();
		try {
			frameRate.ping();
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

	public void newTranslation(boolean newMap, Random rng) {
		translate.changeTable(translateSource.generate(buffer.width, buffer.height, newMap, rng));
		notify(translateSource.getLastGenerated());
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
