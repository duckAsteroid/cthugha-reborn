package io.github.duckasteroid.cthugha.display.phase;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.wave.AmplitudeFunction;
import com.asteroid.duck.opengl.util.wave.AudioWave;
import com.asteroid.duck.opengl.util.wave.RadialSpectrumAnalyser;
import com.asteroid.duck.opengl.util.wave.RadialWave;
import com.asteroid.duck.opengl.util.wave.SpectrumAnalyser;
import io.github.duckasteroid.cthugha.JCthugha;
import io.github.duckasteroid.cthugha.display.AudioPipeline;
import io.github.duckasteroid.cthugha.display.wave.OscilloscopeModel;
import io.github.duckasteroid.cthugha.display.wave.RadialSpectrumModel;
import io.github.duckasteroid.cthugha.display.wave.RadialWaveModel;
import io.github.duckasteroid.cthugha.display.wave.SpectrumModel;
import io.github.duckasteroid.cthugha.params.ParamNode;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders every wave instance in {@code cthugha.waveSystem} (see
 * {@link io.github.duckasteroid.cthugha.display.wave.WaveSystem}) directly into the R16 indexed
 * buffer, in list order -- later entries composite on top of earlier ones.
 *
 * All wave renderers use {@code waveIdx = 1.0f} (the maximum normalised R16 value), which
 * PaletteRenderer resolves to the last palette entry via
 * {@code pixelIndex = sampledValue * totalEntries}. Only the red channel is non-zero; green
 * and blue are 0 so no colour information leaks into the palette lookup.
 *
 * The spectrum/radial-spectrum bar, fill and peak colours are user-tunable via
 * {@link SpectrumModel} / {@link RadialSpectrumModel}, but render-core's {@code SpectrumAnalyser}
 * / {@code RadialSpectrumAnalyser} only accept colours at construction time ({@code withBarColors}
 * / {@code withColors} / {@code withPeakColor} must be called before {@code init()} -- there is no
 * runtime colour setter). To keep those params live-editable from the remote UI, each spectrum
 * entry disposes and rebuilds its analyser on the render thread whenever a colour or show/hide
 * param changes, instead of only applying them once at startup.
 *
 * <p>The set of live GL renderer objects is kept in sync with {@code cthugha.waveSystem}'s
 * instance list by reconciling against it once per frame in {@link #indexedRender} (see
 * {@link #resyncWaves()}) -- waves are added/removed from arbitrary threads (e.g. a remote HTTP
 * request), but the GL renderer objects backing them may only be built or disposed on the render
 * thread. {@code WaveSystem}'s own {@code setOnTreeChanged} slot is left free for
 * {@code CthughaWindow} to wire up remote "tree changed" broadcasts, so this phase doesn't fight
 * it over that single-listener callback.</p>
 */
public class WavePhase implements RenderPhase {

    private static final Logger LOG = LoggerFactory.getLogger(WavePhase.class);

    private final JCthugha cthugha;
    private AudioPipeline audioPipeline;
    private RenderContext initCtx;
    private Vector4f waveColour;

    /** Live GL renderer entries, keyed by model instance, kept in {@code waveSystem} list order. */
    private final Map<ParamNode, WaveEntry> entries = new LinkedHashMap<>();

    public WavePhase(JCthugha cthugha) {
        this.cthugha = cthugha;
    }

    @Override
    public void init(RenderContext ctx) throws IOException {
        this.initCtx = ctx;
        // pos = index / paletteSize so PaletteRenderer resolves pixelIndex = pos * totalEntries = index
        float waveIdx = 1.0f; //200f / cthugha.paletteMap.size();
        waveColour = new Vector4f(waveIdx, 0f, 0f, 1f);

        audioPipeline = new AudioPipeline();
        audioPipeline.init(ctx);
        cthugha.beatDetector = audioPipeline.getBeatDetector();

        cthugha.audioSource.setOnSourceSelected(name -> {
            if (audioPipeline.selectSource(name)) {
                cthugha.notify("audio: " + name);
            } else {
                cthugha.notify("audio: source unavailable");
            }
        });
        cthugha.audioSource.syncSelected(audioPipeline.getSelectedSourceName());

        resyncWaves();
    }

    /**
     * Builds the base transform for {@link SpectrumModel.Position}: a matrix that the user's own
     * {@code transform} composes on top of (see {@code TransformParams#applyTo}), rather than
     * replacing it. BOTTOM is the identity (matches the existing bars-grow-up-from-the-bottom
     * layout); TOP flips vertically so bars hang from the top edge instead; LEFT/RIGHT rotate the
     * whole bar row 90 degrees -- a full axis swap that render-core's BarDirection/BarLayout can't
     * express on their own, since they only control growth direction and bin ordering along the
     * renderer's fixed horizontal/vertical axes.
     */
    private static Matrix4f positionBase(SpectrumModel.Position position) {
        Matrix4f base = new Matrix4f();
        switch (position) {
            case TOP -> base.scale(1f, -1f, 1f);
            case LEFT -> base.rotateZ((float) (-Math.PI / 2));
            case RIGHT -> base.rotateZ((float) (Math.PI / 2));
            case BOTTOM -> { /* identity: already anchored to the bottom edge */ }
        }
        return base;
    }

    @Override
    public void indexedRender(RenderContext ctx) {
        audioPipeline.update();

        resyncWaves();

        for (WaveEntry entry : entries.values()) {
            entry.render(ctx);
        }
    }

    @Override
    public void dispose() {
        entries.values().forEach(WaveEntry::dispose);
        entries.clear();
        if (audioPipeline != null) audioPipeline.dispose();
    }

    /**
     * GL thread only: reconciles {@link #entries} with {@code cthugha.waveSystem}'s current
     * instance list -- disposing renderers for instances that were removed, and building
     * renderers for instances that are new. Only ever appends new entries (v1 has no reordering
     * support), so the {@link LinkedHashMap}'s insertion order continues to match the wave
     * system's render order after every resync.
     */
    private void resyncWaves() {
        List<ParamNode> current = cthugha.waveSystem.instances();
        entries.keySet().removeIf(model -> {
            if (current.contains(model)) return false;
            entries.get(model).dispose();
            return true;
        });
        for (ParamNode model : current) {
            entries.computeIfAbsent(model, this::buildEntry);
        }
    }

    private WaveEntry buildEntry(ParamNode model) {
        try {
            if (model instanceof OscilloscopeModel om) return new OscilloscopeEntry(om);
            if (model instanceof RadialWaveModel rm) return new RadialWaveEntry(rm);
            if (model instanceof SpectrumModel sm) return new SpectrumEntry(sm);
            if (model instanceof RadialSpectrumModel rsm) return new RadialSpectrumEntry(rsm);
            throw new IllegalArgumentException("Unknown wave model type: " + model.getClass());
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to initialise wave renderer for " + model.getFullPath(), e);
        }
    }

    /** One live GL renderer backing a single wave model instance. */
    private interface WaveEntry {
        /** Called every frame, whether or not the instance is currently enabled. */
        void render(RenderContext ctx);

        /** GL thread only: releases this entry's GL resources. */
        void dispose();
    }

    private final class OscilloscopeEntry implements WaveEntry {
        private final OscilloscopeModel model;
        private final AudioWave wave;

        OscilloscopeEntry(OscilloscopeModel model) throws IOException {
            this.model = model;
            wave = new AudioWave(audioPipeline.getPboSink());
            wave.setLineColour(waveColour);
            wave.setClearBeforeRender(false);
            wave.init(initCtx);
        }

        @Override
        public void render(RenderContext ctx) {
            if (!model.enabled.value) return;
            float amp = (float) model.amplitude.value;
            wave.setLineWidth((float) model.lineWidth.value);
            wave.setChannelMode(model.channelMode.getEnumeration().ordinal());
            wave.setAmplitudeFunction(
                    model.ellipse.value ? AmplitudeFunction.ellipse(amp) : AmplitudeFunction.constant(amp));
            wave.setTransform(model.transform.applyTo(new Matrix4f()));
            wave.doRender(ctx);
        }

        @Override
        public void dispose() {
            wave.dispose();
        }
    }

    private final class RadialWaveEntry implements WaveEntry {
        private final RadialWaveModel model;
        private final RadialWave wave;

        RadialWaveEntry(RadialWaveModel model) throws IOException {
            this.model = model;
            wave = new RadialWave(audioPipeline.getPboSink());
            wave.setLineColour(waveColour);
            wave.setClearBeforeRender(false);
            wave.init(initCtx);
        }

        @Override
        public void render(RenderContext ctx) {
            if (!model.enabled.value) return;
            float amp = (float) model.amplitude.value;
            wave.setLineWidth((float) model.lineWidth.value);
            wave.setChannelMode(model.channelMode.getEnumeration().ordinal());
            wave.setAmplitudeFunction(
                    model.ellipse.value ? AmplitudeFunction.ellipse(amp) : AmplitudeFunction.constant(amp));
            wave.setTransform(model.transform.applyTo(new Matrix4f()));
            wave.doRender(ctx);
        }

        @Override
        public void dispose() {
            wave.dispose();
        }
    }

    private final class SpectrumEntry implements WaveEntry {
        private final SpectrumModel model;
        private SpectrumAnalyser analyser;
        private volatile boolean colourDirty = false;

        SpectrumEntry(SpectrumModel model) throws IOException {
            this.model = model;
            analyser = build();
            audioPipeline.getFreqProc().addSink(analyser);
            analyser.init(initCtx);
            Runnable mark = () -> colourDirty = true;
            model.barColorLow.addChangeListener(mark);
            model.barColorHigh.addChangeListener(mark);
            model.peakColor.addChangeListener(mark);
            model.showBars.addChangeListener(mark);
            model.showPeakTicks.addChangeListener(mark);
        }

        private SpectrumAnalyser build() {
            SpectrumAnalyser sa = new SpectrumAnalyser(audioPipeline.getFreqProc())
                    .withBarColors(model.barColorLowVec(), model.barColorHighVec())
                    .withPeakColor(model.peakColorVec());
            sa.setClearBeforeRender(false);
            return sa;
        }

        /** GL thread only: disposes and rebuilds the analyser with current colour params. */
        private void reinit(RenderContext ctx) {
            colourDirty = false;
            audioPipeline.getFreqProc().removeSink(analyser);
            analyser.dispose();
            analyser = build();
            audioPipeline.getFreqProc().addSink(analyser);
            try {
                analyser.init(ctx);
            } catch (IOException e) {
                LOG.error("Failed to reinitialise spectrum analyser after a colour change", e);
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void render(RenderContext ctx) {
            if (colourDirty) reinit(ctx);
            if (!model.enabled.value) return;
            analyser.setTransform(model.transform.applyTo(positionBase(model.position.getEnumeration())));
            analyser.doRender(ctx);
        }

        @Override
        public void dispose() {
            audioPipeline.getFreqProc().removeSink(analyser);
            analyser.dispose();
        }
    }

    private final class RadialSpectrumEntry implements WaveEntry {
        private final RadialSpectrumModel model;
        private RadialSpectrumAnalyser analyser;
        private volatile boolean colourDirty = false;

        RadialSpectrumEntry(RadialSpectrumModel model) throws IOException {
            this.model = model;
            analyser = build();
            audioPipeline.getFreqProc().addSink(analyser);
            analyser.init(initCtx);
            Runnable mark = () -> colourDirty = true;
            model.innerColor.addChangeListener(mark);
            model.baseColor.addChangeListener(mark);
            model.outerColor.addChangeListener(mark);
            model.peakColor.addChangeListener(mark);
            model.showBars.addChangeListener(mark);
            model.showPeakTicks.addChangeListener(mark);
        }

        private RadialSpectrumAnalyser build() {
            RadialSpectrumAnalyser rsa = new RadialSpectrumAnalyser(audioPipeline.getFreqProc())
                    .withColors(model.innerColorVec(), model.baseColorVec(), model.outerColorVec())
                    .withPeakColor(model.peakColorVec());
            rsa.setClearBeforeRender(false);
            return rsa;
        }

        /** GL thread only: disposes and rebuilds the analyser with current colour params. */
        private void reinit(RenderContext ctx) {
            colourDirty = false;
            audioPipeline.getFreqProc().removeSink(analyser);
            analyser.dispose();
            analyser = build();
            audioPipeline.getFreqProc().addSink(analyser);
            try {
                analyser.init(ctx);
            } catch (IOException e) {
                LOG.error("Failed to reinitialise radial spectrum analyser after a colour change", e);
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void render(RenderContext ctx) {
            if (colourDirty) reinit(ctx);
            if (!model.enabled.value) return;
            analyser.withRepeats(model.repeats.value);
            analyser.setTransform(model.transform.applyTo(new Matrix4f()));
            analyser.doRender(ctx);
        }

        @Override
        public void dispose() {
            audioPipeline.getFreqProc().removeSink(analyser);
            analyser.dispose();
        }
    }
}
