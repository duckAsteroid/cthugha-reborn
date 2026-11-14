package com.asteroid.duck.opengl.util.audio;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.audio.simulated.SimulatedSources;
import com.asteroid.duck.opengl.util.keys.KeyCombination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Live audio capture with sensible defaults: one call to start capturing from the system's
 * default input into a set of {@link AudioSink}s.
 *
 * <p>Rather than offering every ALSA device Java Sound can see (raw {@code plughw:} devices
 * bypass PipeWire and are often busy), the sources are just:</p>
 * <ol>
 *   <li>the system default capture line — the one routed through PipeWire/PulseAudio. By
 *       default that records the default <em>source</em> (usually the microphone). To record
 *       whatever is playing instead, set the environment variable
 *       {@code PIPEWIRE_PROPS='{ stream.capture.sink=true }'} (the experiments' {@code run} task
 *       does): PipeWire then connects the capture stream to the default sink's monitor, without
 *       changing any system setting; and</li>
 *   <li>a simulated middle C, always available, as a fallback and for testing without audio.</li>
 * </ol>
 * <p>The default line is chosen by name: the first {@link LineAcquirer#IDEAL}-format line whose
 * mixer name contains {@code pipewire}, then {@code pulse}, then {@code [default]}. Set the
 * system property {@value #SOURCE_PROPERTY} to override it: {@code simulated}, or part of any
 * line's name (e.g. {@code -Daudio.source=plughw:1,0}).</p>
 *
 * <p>Capture runs on a platform thread (as {@link AudioReader} recommends) under a supervisor
 * that restarts it if a line implementation throws, up to {@value #MAX_RESTARTS} times in
 * {@value #RESTART_WINDOW_MS} ms.</p>
 *
 * <pre>{@code
 * audioInput = AudioInput.start(ctx, List.of(pboSink, frequencyProcessor));
 * audioInput.bindKeys(ctx);  // J / H switch source
 * // ...
 * audioInput.close();
 * }</pre>
 */
public final class AudioInput implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(AudioInput.class);

    /** System property naming the source to start with: {@code simulated} or part of a line name. */
    public static final String SOURCE_PROPERTY = "audio.source";
    /** Mixer-name fragments identifying the system default line, most preferred first. */
    private static final List<String> PREFERRED = List.of("pipewire", "pulse", "[default]");
    private static final String SIMULATED = "simulated";
    static final int MAX_RESTARTS = 5;
    static final long RESTART_WINDOW_MS = 10_000;

    private final List<AudioSink> sinks;
    private final List<AudioDataSource> sources;
    private int selected;
    private AudioReader reader;
    private Thread thread;
    private int restarts;
    private long restartWindowStart;
    private boolean closed;

    private AudioInput(List<AudioSink> sinks, List<AudioDataSource> sources, int selected) {
        this.sinks = List.copyOf(sinks);
        this.sources = List.copyOf(sources);
        this.selected = selected;
    }

    /**
     * Discover the sources and start capturing from the default one.
     * @param ctx   render context (the simulated source runs on its clock)
     * @param sinks where captured PCM goes; written on the capture thread
     * @return the running input
     */
    public static AudioInput start(RenderContext ctx, List<AudioSink> sinks) {
        List<LineAcquirer.MixerLine> lines = LineAcquirer.allLinesMatching(LineAcquirer.IDEAL).toList();
        List<AudioDataSource> sources = new ArrayList<>();
        String requested = System.getProperty(SOURCE_PROPERTY, "").trim();

        Optional<LineAcquirer.MixerLine> line = requested.isEmpty() || requested.equalsIgnoreCase(SIMULATED)
                ? defaultLine(lines)
                : find(lines, requested).or(() -> {
                    LOG.warn("No audio line matching {}={}; using the default", SOURCE_PROPERTY, requested);
                    return defaultLine(lines);
                });
        line.ifPresent(l -> sources.add(l.toAudioDataSource()));
        sources.add(SimulatedSources.middleC(ctx.getClock()));
        if (line.isEmpty()) LOG.warn("No usable audio capture line found; using the simulated source");

        int selected = requested.equalsIgnoreCase(SIMULATED) ? sources.size() - 1 : 0;
        AudioInput input = new AudioInput(sinks, sources, selected);
        input.startThread();
        input.reader.setLine(input.sources.get(selected));
        LOG.info("Audio input: {}", input.sourceName());
        return input;
    }

    private static Optional<LineAcquirer.MixerLine> defaultLine(List<LineAcquirer.MixerLine> lines) {
        for (String fragment : PREFERRED) {
            Optional<LineAcquirer.MixerLine> match = find(lines, fragment);
            if (match.isPresent()) return match;
        }
        return lines.stream().findFirst();
    }

    private static Optional<LineAcquirer.MixerLine> find(List<LineAcquirer.MixerLine> lines, String fragment) {
        String wanted = fragment.toLowerCase(Locale.ROOT);
        return lines.stream()
                .filter(l -> l.displayName().toLowerCase(Locale.ROOT).contains(wanted))
                .findFirst();
    }

    private synchronized void startThread() {
        reader = new AudioReader(sinks);
        thread = Thread.ofPlatform().name("audio-capture").daemon()
                .uncaughtExceptionHandler(this::onCaptureDied)
                .start(reader);
    }

    private synchronized void onCaptureDied(Thread dead, Throwable error) {
        if (closed) return;
        LOG.error("Audio capture thread died; restarting", error);
        long now = System.currentTimeMillis();
        if (now - restartWindowStart > RESTART_WINDOW_MS) {
            restartWindowStart = now;
            restarts = 0;
        }
        if (++restarts > MAX_RESTARTS) {
            LOG.error("Audio capture died {} times within {} ms; giving up", restarts, RESTART_WINDOW_MS);
            return;
        }
        startThread();
        reader.setLine(sources.get(selected));
    }

    /**
     * Bind <kbd>J</kbd> / <kbd>H</kbd> to the next / previous source.
     * @param ctx render context whose key registry to use
     */
    public void bindKeys(RenderContext ctx) {
        ctx.getKeyRegistry().registerKeyAction(KeyCombination.simple('J'), this::next, "Switch to the next audio input");
        ctx.getKeyRegistry().registerKeyAction(KeyCombination.simple('H'), this::previous, "Switch to the previous audio input");
    }

    /** Switch to the next source. */
    public void next() {
        select((selected + 1) % sources.size());
    }

    /** Switch to the previous source. */
    public void previous() {
        select((selected - 1 + sources.size()) % sources.size());
    }

    private synchronized void select(int index) {
        selected = index;
        reader.setLine(sources.get(index));
        LOG.info("Audio input: {}", sourceName());
    }

    /** @return the name of the source being captured */
    public synchronized String sourceName() {
        return sources.get(selected).getName();
    }

    /** @return the names of the available sources, in {@link #next()} order */
    public List<String> sourceNames() {
        return sources.stream().map(AudioDataSource::getName).toList();
    }

    /** Stop capturing, close the line and wait (up to 2 s) for the capture thread to finish. */
    @Override
    public void close() {
        Thread toJoin;
        synchronized (this) {
            if (closed) return;
            closed = true;
            reader.setRunning(false);
            reader.setLine(null);
            toJoin = thread;
        }
        toJoin.interrupt();
        try {
            toJoin.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
