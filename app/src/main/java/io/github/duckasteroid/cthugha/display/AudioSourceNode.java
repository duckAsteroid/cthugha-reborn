package io.github.duckasteroid.cthugha.display;

import com.asteroid.duck.opengl.util.audio.LineAcquirer;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.action.AbstractAction;
import io.github.duckasteroid.cthugha.params.values.EnumParameter;

import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Lists the audio capture devices Java Sound can see and lets the user pick one, as a
 * top-level "Audio" tab.
 *
 * <p>The param tree is assembled by {@code ActionTreeBuilder} before any {@code RenderPhase}
 * (and therefore before {@link AudioPipeline}, which owns the live, GL-bound
 * {@code LineAcquirer} used for actual capture) has been initialised. So this node discovers
 * device names independently via {@link LineAcquirer}'s static mixer scan, and the caller
 * (see {@link #setOnSourceSelected}) reconciles a selection against the live pipeline by exact
 * device name once it exists. If the two scans disagree (e.g. a line that fails to open, or
 * the {@code simulate.audio=true} synthetic source, which isn't visible to a static scan and
 * so never appears here), the caller's reconciliation simply reports "not found" rather than
 * silently selecting the wrong device.</p>
 */
public class AudioSourceNode extends ParamNode {

    private final EnumParameter<String> selector;
    private final Random rng = new Random();
    private Consumer<String> onSourceSelected;
    private boolean syncing = false;

    public AudioSourceNode() {
        this(discoverNames());
    }

    /** Package-visible for testing with a fixed device list instead of scanning real hardware. */
    AudioSourceNode(List<String> names) {
        super("Audio");
        withUiHint(UiHint.ICON, "mic");
        withDescription("The audio capture device feeding the waveform and spectrum visualisations.");

        selector = new EnumParameter<>("Source", names);
        selector.withDescription("Which Java Sound capture device is currently active. "
            + "Changing it switches live capture to that device.");
        // Environment-specific (depends on what hardware is plugged into this machine), not
        // part of the visual configuration, so it's excluded from screen-config snapshots.
        selector.withNoPersist();
        selector.addChangeListener(() -> {
            if (syncing || onSourceSelected == null) return;
            onSourceSelected.accept(selector.getEnumeration());
        });

        AbstractAction random = new AbstractAction("Random", ctx ->
            selector.setValue(rng.nextInt(selector.getOptions().size())));
        random.withUiHint(UiHint.ICON, "shuffle");
        random.withDescription("Switches to a random audio capture device from the list.");

        addChild(selector);
        addChild(random);
    }

    /** Registers the callback invoked when the user picks a different source by name. */
    public void setOnSourceSelected(Consumer<String> callback) {
        this.onSourceSelected = callback;
    }

    /**
     * Syncs the displayed selection to the device name the live {@link AudioPipeline} actually
     * ended up with (e.g. after its own preferred-source heuristic), without re-firing
     * {@link #setOnSourceSelected}. No-ops if the name isn't among the discovered options.
     */
    public void syncSelected(String name) {
        int idx = selector.getOptions().indexOf(name);
        if (idx < 0) return;
        syncing = true;
        selector.setValue(idx);
        syncing = false;
    }

    private static List<String> discoverNames() {
        return LineAcquirer.allLinesMatching(LineAcquirer.IDEAL)
                .map(Object::toString)
                .collect(Collectors.toList());
    }
}
