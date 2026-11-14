package io.github.duckasteroid.cthugha.display;

import io.github.duckasteroid.cthugha.config.Config;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.values.EnumParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;

import java.time.Duration;
import java.util.Arrays;

/**
 * Live-editable "Beat Tracker" settings under the Audio tab: the latency the tempo tracker's
 * visuals lead the analysis by, and the {@link BeatClock.TempoScale} correction for a tracker
 * locked at double or half tempo. Both persist to {@code [BeatTracker]} in {@code cthugha.ini}
 * on change and notify {@link #setOnChanged}.
 *
 * <p>Excluded from screen configs: latency depends on this machine's audio and display chain,
 * not on the visualisation.</p>
 */
public class BeatTrackerSettingsNode extends ParamNode {

    private static final String SECTION = "BeatTracker";
    public static final int MIN_LATENCY_MS = -100;
    public static final int MAX_LATENCY_MS = 300;

    public final IntegerParameter latency;
    public final EnumParameter<BeatClock.TempoScale> tempoScale;

    private Runnable onChanged;

    public BeatTrackerSettingsNode() {
        this(Config.singleton());
    }

    /** Package-visible for testing against an explicit {@link Config}. */
    BeatTrackerSettingsNode(Config cfg) {
        super("Beat Tracker");
        withUiHint(UiHint.ICON, "timer");
        withDescription("Tracks the tempo and predicts each beat, so visuals land on the beat "
            + "rather than after it (bpm(), beatPhase(), beatPulse(), kick() … in binding scripts, "
            + "and the tempo rows of the Debug Beats overlay). Takes ~3 s to lock on.");
        withNoPersist();

        int latencyMs = Math.clamp(cfg.getConfigAs(SECTION, "latency_ms", "0", Integer::parseInt),
                MIN_LATENCY_MS, MAX_LATENCY_MS);
        latency = new IntegerParameter("Latency (ms)", MIN_LATENCY_MS, MAX_LATENCY_MS, latencyMs);
        latency.withDescription("How far the beat leads the analysed audio, in milliseconds. "
            + "Raise it (typically 20–80) if pulses land after the beat you hear, because of "
            + "audio output and display delay.");
        latency.withNoAnimate();
        latency.addChangeListener(() -> {
            cfg.setConfig(SECTION, "latency_ms", String.valueOf(latency.value));
            fireChanged();
        });

        tempoScale = new EnumParameter<>("Tempo", Arrays.asList(BeatClock.TempoScale.values()),
                parseScale(cfg.getConfig(SECTION, "tempo_scale", "")));
        tempoScale.withDescription("Corrects a tempo locked at the wrong speed: Half if the beat "
            + "pulses twice as fast as the music feels, Double if half as fast.");
        tempoScale.withNoAnimate();
        tempoScale.addChangeListener(() -> {
            cfg.setConfig(SECTION, "tempo_scale", tempoScale.getEnumeration().name());
            fireChanged();
        });

        addChild(latency);
        addChild(tempoScale);
    }

    /** Registers a callback fired (on the calling thread) whenever any field changes. */
    public void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    public Duration latency() {
        return Duration.ofMillis(latency.value);
    }

    public BeatClock.TempoScale tempoScale() {
        return tempoScale.getEnumeration();
    }

    private void fireChanged() {
        if (onChanged != null) onChanged.run();
    }

    private static BeatClock.TempoScale parseScale(String value) {
        try {
            return BeatClock.TempoScale.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return BeatClock.TempoScale.NORMAL;
        }
    }
}
