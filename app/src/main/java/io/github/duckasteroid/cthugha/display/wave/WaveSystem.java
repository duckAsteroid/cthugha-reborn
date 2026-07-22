package io.github.duckasteroid.cthugha.display.wave;

import io.github.duckasteroid.cthugha.params.DynamicChildList;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.action.AbstractAction;
import io.github.duckasteroid.cthugha.params.values.EnumParameter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages a dynamic, ordered list of wave visualisation instances — the "Wave" tab — following
 * the same {@code CopyOnWriteArrayList} + {@code addChild}/{@code removeChild} + tree-sync
 * pattern already used by {@code io.github.duckasteroid.cthugha.binding.BindingSystem} for its
 * list of {@code Binding}s.
 *
 * <p>Where the app used to hardwire exactly one {@link OscilloscopeModel}, {@link RadialWaveModel},
 * {@link SpectrumModel} and {@link RadialSpectrumModel} as fixed fields (each with its own
 * {@code enabled} toggle), this node instead holds any number of independently-configured
 * instances of any of those four types, auto-named {@code "{Type} {N}"} (e.g. "Oscilloscope 1",
 * "Oscilloscope 2") in the order they were added. Render order (see {@code WavePhase}) matches
 * list order, so later entries composite on top of earlier ones — reordering is not supported in
 * v1, matching the issue's scope.</p>
 *
 * <p>Each instance reuses the existing wave model {@code ParamNode} classes exactly as they were
 * before this change (colour/position/channel-mode params etc. from issue #4) — this node just
 * instantiates them under a unique name and appends a "Delete" action as an extra child, rather
 * than reimplementing anything wave-specific.</p>
 *
 * <p>Implements {@link DynamicChildList} so a screen config can round-trip the current set of
 * waves: {@link #describe()} captures each instance's name and {@link WaveType}, and
 * {@link #recreate(List)} clears the current list and rebuilds it via {@link #addWaveNamed},
 * preserving each instance's original name so leaf paths captured elsewhere in the same snapshot
 * (e.g. {@code Wave/Oscilloscope 1/amplitude}) resolve against the recreated child.</p>
 */
public class WaveSystem extends ParamNode implements DynamicChildList {

    /** The four wave visualisation types that can be added; {@link #label} is also the auto-naming prefix. */
    public enum WaveType {
        OSCILLOSCOPE(OscilloscopeModel.DEFAULT_NAME),
        RADIAL_WAVE(RadialWaveModel.DEFAULT_NAME),
        SPECTRUM(SpectrumModel.DEFAULT_NAME),
        RADIAL_SPECTRUM(RadialSpectrumModel.DEFAULT_NAME);

        private final String label;

        WaveType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        private ParamNode newInstance(String name) {
            return switch (this) {
                case OSCILLOSCOPE -> new OscilloscopeModel(name);
                case RADIAL_WAVE -> new RadialWaveModel(name);
                case SPECTRUM -> new SpectrumModel(name);
                case RADIAL_SPECTRUM -> new RadialSpectrumModel(name);
            };
        }

        private static WaveType of(ParamNode model) {
            if (model instanceof OscilloscopeModel) return OSCILLOSCOPE;
            if (model instanceof RadialWaveModel) return RADIAL_WAVE;
            if (model instanceof SpectrumModel) return SPECTRUM;
            if (model instanceof RadialSpectrumModel) return RADIAL_SPECTRUM;
            throw new IllegalArgumentException("Not a known wave model type: " + model.getClass());
        }
    }

    private final CopyOnWriteArrayList<ParamNode> waves = new CopyOnWriteArrayList<>();
    private final Map<WaveType, AtomicInteger> counters = new EnumMap<>(WaveType.class);

    private final EnumParameter<WaveType> newType =
            new EnumParameter<>("New Type", Arrays.asList(WaveType.values()));
    private final AbstractAction addWaveAction;

    private volatile Runnable onTreeChanged = () -> {};

    public WaveSystem() {
        super("Wave");
        withUiHint(UiHint.ICON, "music");
        withDescription("Audio-reactive wave visualisations rendered directly into the "
            + "palette-indexed buffer. Add multiple independently-configured instances of the "
            + "same type to layer them -- later entries in the list render on top of earlier ones.");

        for (WaveType t : WaveType.values()) {
            counters.put(t, new AtomicInteger());
        }

        newType.withUiHint(UiHint.CONTROL_TYPE, UiHint.LIST);
        newType.withUiHint(UiHint.ICON, "list");
        newType.withDescription("Type of wave visualisation to add.");
        newType.withNoPersist();

        addWaveAction = new AbstractAction("New Wave", ctx -> addWave(newType.getEnumeration()));
        addWaveAction.withUiHint(UiHint.ICON, "circle-plus");

        addChild(newType);
        addChild(addWaveAction);
    }

    /**
     * Adds a new instance of {@code type}, auto-named {@code "{label} {nextIndexForType}"}
     * (e.g. "Oscilloscope 1", then "Oscilloscope 2"), appended to the end of the list (i.e.
     * rendered on top of every existing instance).
     */
    public ParamNode addWave(WaveType type) {
        String name = type.label() + " " + counters.get(type).incrementAndGet();
        return addWaveNamed(type, name);
    }

    /**
     * Same as {@link #addWave(WaveType)} but with an explicit name rather than an auto-generated
     * one — used by {@link #recreate(List)} so a recreated instance's path matches the one a
     * snapshot's leaf-value entries were captured under. Also bumps the auto-naming counter for
     * {@code type} past {@code name} if it looks like an auto-generated {@code "{label} N"} name,
     * so a later auto-named addition doesn't collide with it.
     */
    private ParamNode addWaveNamed(WaveType type, String name) {
        ParamNode model = type.newInstance(name);
        model.addChild(deleteAction(model));
        waves.add(model);
        addChild(model);
        onTreeChanged.run();
        bumpCounterPast(type, name);
        return model;
    }

    private AbstractAction deleteAction(ParamNode model) {
        AbstractAction delete = new AbstractAction("Delete", ctx -> removeWave(model));
        delete.withUiHint(UiHint.ICON, "trash-2");
        return delete;
    }

    /** Removes a wave instance from the list and detaches it from the param tree. */
    public void removeWave(ParamNode model) {
        waves.remove(model);
        removeChild(model);
        onTreeChanged.run();
    }

    private void bumpCounterPast(WaveType type, String name) {
        String prefix = type.label() + " ";
        if (name == null || !name.startsWith(prefix)) return;
        try {
            int n = Integer.parseInt(name.substring(prefix.length()).trim());
            counters.get(type).updateAndGet(cur -> Math.max(cur, n));
        } catch (NumberFormatException ignored) {
            // not an auto-generated name; nothing to bump
        }
    }

    /** The current wave instances, in render order (list/declaration order). */
    public List<ParamNode> instances() {
        return List.copyOf(waves);
    }

    /** Registers a callback invoked whenever a wave instance is added or removed. */
    public void setOnTreeChanged(Runnable r) {
        this.onTreeChanged = r != null ? r : () -> {};
    }

    /**
     * {@inheritDoc}
     *
     * <p>Captures each instance's {@link WaveType} as the {@code type} discriminator and its
     * name — enough for {@link #recreate(List)} to rebuild an equivalent instance via
     * {@link #addWaveNamed}. Per-instance param values (amplitude, colour, transform, etc.) are
     * captured separately as ordinary leaf values by {@code ScreenConfigParams}, once the
     * instance itself exists.</p>
     */
    @Override
    public List<ChildSpec> describe() {
        List<ChildSpec> specs = new ArrayList<>(waves.size());
        for (ParamNode model : waves) {
            specs.add(new ChildSpec(model.getName(), WaveType.of(model).name(), Map.of()));
        }
        return specs;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Removes every current wave instance (via {@link #removeWave}) then rebuilds the list
     * from {@code specs} in order, preserving each instance's original name.</p>
     */
    @Override
    public void recreate(List<ChildSpec> specs) {
        new ArrayList<>(waves).forEach(this::removeWave);
        for (ChildSpec spec : specs) {
            addWaveNamed(WaveType.valueOf(spec.type()), spec.name());
        }
    }
}
