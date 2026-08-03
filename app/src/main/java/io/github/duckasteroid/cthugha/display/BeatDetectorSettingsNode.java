package io.github.duckasteroid.cthugha.display;

import com.asteroid.duck.opengl.util.audio.analysis.FrequencyBand;
import io.github.duckasteroid.cthugha.config.Config;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;

import java.util.ArrayList;
import java.util.List;

/**
 * Live-editable "Beat Detector" settings under the Audio tab: per-band Hz ranges plus the
 * detector's global threshold/sensitivity/decay/history knobs. Every field persists to
 * {@code cthugha.ini} on change (see {@link BeatDetectorConfig}) and notifies {@link
 * #setOnChanged} so the owner can rebuild the live {@code BeatDetector}.
 *
 * <p>The set of bands (names, count, order) is fixed at construction time from {@link
 * BeatDetectorConfig#load()} — only each band's Hz range and the four global knobs are editable
 * here. Adding/removing/renaming bands still requires hand-editing {@code [beats]} and
 * restarting, since {@link io.github.duckasteroid.cthugha.display.phase.DebugBeatsPhase} caches
 * band identity at {@code init()}.</p>
 *
 * <p>Also captured (as a nested subtree) by whole-tree "Configs" screen-config snapshots, in
 * addition to its own separate named-preset mechanism scoped to just this subtree (see {@code
 * beatpresets/}) — this node cannot opt out of the former via {@code withNoPersist()} without
 * breaking the latter, since {@link io.github.duckasteroid.cthugha.screenconfig.ScreenConfigParams
 * #capture} checks persist-exclusion on its root argument too, and this node is also that root
 * for a beat-preset capture.</p>
 */
public class BeatDetectorSettingsNode extends ParamNode {

    private static final String BANDS_SECTION = "beats";
    private static final String TUNING_SECTION = "BeatDetector";
    private static final int HZ_MIN = 20;
    private static final int HZ_MAX = 20_000;

    private final List<BandNode> bandNodes = new ArrayList<>();
    public final DoubleParameter threshold;
    public final DoubleParameter sensitivity;
    public final DoubleParameter decay;
    public final IntegerParameter history;

    private Runnable onChanged;

    public BeatDetectorSettingsNode() {
        super("Beat Detector");
        withUiHint(UiHint.ICON, "activity");
        // Deliberately NOT withNoPersist(): this subtree is also captured directly as the root of
        // a beat-preset snapshot (see BeatPresetStore), and ScreenConfigParams.capture/apply check
        // isPersistExcluded() on the passed-in root itself, not just descendants -- excluding this
        // node would make every preset save silently capture nothing. As a side effect, Beat
        // Detector settings are now also included in whole-tree "Configs" screen-config snapshots,
        // which is harmless (just more captured leaf values).
        withDescription("Frequency bands and tuning monitored by beat detection "
            + "(bass()/snare()/hihat() in binding scripts, and the Debug Beats overlay). "
            + "Changes here take effect live and are saved to cthugha.ini.");

        BeatDetectorConfig.Tuning tuning = BeatDetectorConfig.loadTuning();

        for (FrequencyBand band : BeatDetectorConfig.load()) {
            BandNode bandNode = new BandNode(band);
            bandNodes.add(bandNode);
            addChild(bandNode);
        }

        threshold = new DoubleParameter("Threshold", 1.0, 5.0, tuning.threshold());
        threshold.withDescription("Ratio above the rolling average energy required to start "
            + "triggering a beat, e.g. 1.3 = 30% louder than average.");
        threshold.addChangeListener(this::onTuningChanged);

        sensitivity = new DoubleParameter("Sensitivity", 0.1, 10.0, tuning.sensitivity());
        sensitivity.withDescription("Scales how quickly strength above Threshold reaches 1.0; "
            + "higher values reach full strength at a lower peak.");
        sensitivity.addChangeListener(this::onTuningChanged);

        decay = new DoubleParameter("Decay", 0.0, 0.5, tuning.decayPerFrame());
        decay.withDescription("Per-frame fall rate of the published beat strength (assuming "
            + "~60fps); e.g. 1/60 causes full scale to decay to zero in one second.");
        decay.addChangeListener(this::onTuningChanged);

        history = new IntegerParameter("History", 1, 300, tuning.historyLength());
        history.withDescription("Number of past frames used for the rolling energy average.");
        history.addChangeListener(this::onTuningChanged);

        addChild(threshold);
        addChild(sensitivity);
        addChild(decay);
        addChild(history);
    }

    /** Registers a callback fired (on the calling thread) whenever any field changes. */
    public void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    /** Reassembles the current band list, in the fixed order established at construction. */
    public List<FrequencyBand> getBands() {
        List<FrequencyBand> bands = new ArrayList<>(bandNodes.size());
        for (BandNode node : bandNodes) {
            bands.add(node.toFrequencyBand());
        }
        return bands;
    }

    /** Reassembles the current global tuning from the four flat fields. */
    public BeatDetectorConfig.Tuning getTuning() {
        return new BeatDetectorConfig.Tuning(history.value, (float) threshold.value,
                (float) sensitivity.value, (float) decay.value);
    }

    private void onTuningChanged() {
        Config.singleton().setConfig(TUNING_SECTION, "threshold", String.valueOf(threshold.value));
        Config.singleton().setConfig(TUNING_SECTION, "sensitivity", String.valueOf(sensitivity.value));
        Config.singleton().setConfig(TUNING_SECTION, "decay", String.valueOf(decay.value));
        Config.singleton().setConfig(TUNING_SECTION, "history", String.valueOf(history.value));
        fireChanged();
    }

    /**
     * Persists every band's current range to {@code [beats]}, not just the one that changed.
     * {@link BeatDetectorConfig#load()} treats "keys present in the section" as the complete band
     * list — writing only the touched band would silently drop every other (untouched) band from
     * {@code [beats]} the moment the section becomes non-empty, deleting it from the monitored set
     * on the next restart even though its slider was never touched.
     */
    private void persistAllBands() {
        for (BandNode node : bandNodes) {
            Config.singleton().setConfig(BANDS_SECTION, node.bandName, node.minHz.value + "-" + node.maxHz.value);
        }
    }

    private void fireChanged() {
        if (onChanged != null) onChanged.run();
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** One monitored band's editable Min/Max Hz range. */
    private final class BandNode extends ParamNode {
        private final String bandName;
        final IntegerParameter minHz;
        final IntegerParameter maxHz;

        BandNode(FrequencyBand band) {
            super(capitalize(band.name()));
            this.bandName = band.name();
            minHz = new IntegerParameter("Min Hz", HZ_MIN, HZ_MAX, Math.round(band.fMin()));
            maxHz = new IntegerParameter("Max Hz", HZ_MIN, HZ_MAX, Math.round(band.fMax()));
            minHz.withDescription("Lower edge (Hz) of the '" + bandName + "' band.");
            maxHz.withDescription("Upper edge (Hz) of the '" + bandName + "' band.");
            minHz.addChangeListener(this::onBandChanged);
            maxHz.addChangeListener(this::onBandChanged);
            addChild(minHz);
            addChild(maxHz);
        }

        FrequencyBand toFrequencyBand() {
            return new FrequencyBand(bandName, minHz.value, maxHz.value);
        }

        private void onBandChanged() {
            persistAllBands();
            fireChanged();
        }
    }
}
