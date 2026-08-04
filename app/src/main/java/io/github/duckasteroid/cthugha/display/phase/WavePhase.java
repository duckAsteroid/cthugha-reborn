package io.github.duckasteroid.cthugha.display.phase;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.wave.AmplitudeFunction;
import com.asteroid.duck.opengl.util.wave.AudioWave;
import com.asteroid.duck.opengl.util.wave.RadialSpectrumAnalyser;
import com.asteroid.duck.opengl.util.wave.RadialWave;
import com.asteroid.duck.opengl.util.wave.SpectrumAnalyser;
import io.github.duckasteroid.cthugha.JCthugha;
import io.github.duckasteroid.cthugha.binding.ScriptHelpers;
import io.github.duckasteroid.cthugha.display.AudioPipeline;
import io.github.duckasteroid.cthugha.display.wave.OscilloscopeModel;
import io.github.duckasteroid.cthugha.display.wave.RadialClockAnalyser;
import io.github.duckasteroid.cthugha.display.wave.RadialClockModel;
import io.github.duckasteroid.cthugha.display.wave.RadialSpectrumModel;
import io.github.duckasteroid.cthugha.display.wave.RadialWaveModel;
import io.github.duckasteroid.cthugha.display.wave.SpectrumModel;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.RenderMode;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;

/**
 * Renders every wave instance in {@code cthugha.waveSystem} (see
 * {@link io.github.duckasteroid.cthugha.display.wave.WaveSystem}), in list order -- later
 * entries composite on top of earlier ones -- into whichever of two targets its own
 * {@code mode} ({@link RenderMode}) selects:
 * <ul>
 *   <li>{@link RenderMode#BUFFER} (default): baked into the R16 palette-indexed render buffer
 *       during {@link #indexedRender}, coloured by a palette index (0-1, stored in the red
 *       channel only -- green/blue are 0 so no colour information leaks into the palette
 *       lookup) -- subject to blur/translate like the rest of the visualisation.</li>
 *   <li>{@link RenderMode#OVERLAY}: drawn directly onto the window's RGBA framebuffer during
 *       {@link #screenRender}, coloured by a real RGB colour ({@code ColorParam}) -- crisp,
 *       immune to blur/translate.</li>
 *   <li>{@link RenderMode#BOTH}: both of the above, once each per frame.</li>
 * </ul>
 *
 * The spectrum/radial-spectrum/radial-clock bar, fill and peak colours are user-tunable via
 * {@link SpectrumModel} / {@link RadialSpectrumModel} / {@code RadialClockModel} (one index
 * param + one {@code ColorParam} per gradient stop), but render-core's {@code SpectrumAnalyser}
 * / {@code RadialSpectrumAnalyser} / {@code RadialClockAnalyser} only accept colours at
 * construction time ({@code withBarColors} / {@code withColors} / {@code withPeakColor} must be
 * called before {@code init()} -- there is no runtime colour setter), and BUFFER's index colours
 * vs. OVERLAY's RGB colours are simply different values, not something a single instance can be
 * reconfigured to emit per-draw. So each of those three entry types keeps two independently
 * live analyser instances -- one built with the index (BUFFER) colours, one with the RGB
 * (OVERLAY) colours -- both registered as sinks on the shared {@code FrequencyProcessor} (so
 * both see identical spectrum data), each disposed and rebuilt on the render thread only when
 * its own colour/show-hide params change. This also means each analyser's {@code doRender} (and
 * the peak-hold ballistics it advances as a side effect) is called at most once per real frame,
 * regardless of {@code mode} -- no double-speed peak decay in {@link RenderMode#BOTH}.
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

    /** Live GL renderer entries, keyed by model instance, kept in {@code waveSystem} list order. */
    private final Map<ParamNode, WaveEntry> entries = new LinkedHashMap<>();

    public WavePhase(JCthugha cthugha) {
        this.cthugha = cthugha;
    }

    @Override
    public void init(RenderContext ctx) throws IOException {
        this.initCtx = ctx;

        audioPipeline = new AudioPipeline();
        audioPipeline.init(ctx);
        cthugha.beatDetector = audioPipeline.getBeatDetector();
        audioPipeline.setOnBeatDetectorRebuilt(bd -> {
            cthugha.beatDetector = bd;
            ScriptHelpers.setContext(bd, cthugha.rng);
        });
        cthugha.audioSource.beatDetectorSettings.setOnChanged(() ->
            audioPipeline.requestBeatDetectorReload(
                cthugha.audioSource.beatDetectorSettings.getBands(),
                cthugha.audioSource.beatDetectorSettings.getTuning()));

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
            if (entry.mode() != RenderMode.OVERLAY) entry.render(ctx, false);
        }
    }

    /**
     * Screen-space pass for waves in {@link RenderMode#OVERLAY}/{@link RenderMode#BOTH}: the
     * same GL draw calls as the indexed pass, but issued while the window's RGBA framebuffer is
     * bound instead of the palette-indexed render texture and coloured by each entry's RGB
     * {@code ColorParam} instead of its palette index, so the result is a crisp overlay immune
     * to blur/translate.
     */
    @Override
    public void screenRender(RenderContext ctx) {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        for (WaveEntry entry : entries.values()) {
            if (entry.mode() != RenderMode.BUFFER) entry.render(ctx, true);
        }
        glDisable(GL_BLEND);
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
            if (model instanceof RadialClockModel rcm) return new RadialClockEntry(rcm);
            throw new IllegalArgumentException("Unknown wave model type: " + model.getClass());
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to initialise wave renderer for " + model.getFullPath(), e);
        }
    }

    /** One live GL renderer backing a single wave model instance. */
    private interface WaveEntry {
        /**
         * Called every frame, whether or not the instance is currently enabled. {@code
         * overlayPass} is {@code false} from {@link #indexedRender} (draw into the palette-indexed
         * buffer, coloured by index) and {@code true} from {@link #screenRender} (draw onto the
         * screen framebuffer, coloured by RGB) -- see {@link #mode()} for which pass(es) an entry
         * is actually called from.
         */
        void render(RenderContext ctx, boolean overlayPass);

        /** The instance's current render mode -- governs which pass(es) {@link #render} is called from. */
        RenderMode mode();

        /** GL thread only: releases this entry's GL resources. */
        void dispose();
    }

    private final class OscilloscopeEntry implements WaveEntry {
        private final OscilloscopeModel model;
        private final AudioWave wave;

        OscilloscopeEntry(OscilloscopeModel model) throws IOException {
            this.model = model;
            wave = new AudioWave(audioPipeline.getPboSink());
            wave.setClearBeforeRender(false);
            wave.init(initCtx);
        }

        @Override
        public RenderMode mode() {
            return model.mode.getEnumeration();
        }

        @Override
        public void render(RenderContext ctx, boolean overlayPass) {
            if (!model.enabled.value) return;
            float amp = (float) model.amplitude.value;
            wave.setLineWidth((float) model.lineWidth.value);
            wave.setChannelMode(model.channelMode.getEnumeration().ordinal());
            wave.setAmplitudeFunction(
                    model.ellipse.value ? AmplitudeFunction.ellipse(amp) : AmplitudeFunction.constant(amp));
            wave.setTransform(model.transform.applyTo(new Matrix4f()));
            wave.setLineColour(overlayPass
                    ? model.color.toVector4f(1f)
                    : new Vector4f((float) model.index.value, 0f, 0f, 1f));
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
            wave.setClearBeforeRender(false);
            wave.init(initCtx);
        }

        @Override
        public RenderMode mode() {
            return model.mode.getEnumeration();
        }

        @Override
        public void render(RenderContext ctx, boolean overlayPass) {
            if (!model.enabled.value) return;
            float amp = (float) model.amplitude.value;
            wave.setLineWidth((float) model.lineWidth.value);
            wave.setChannelMode(model.channelMode.getEnumeration().ordinal());
            wave.setAmplitudeFunction(
                    model.ellipse.value ? AmplitudeFunction.ellipse(amp) : AmplitudeFunction.constant(amp));
            wave.setTransform(model.transform.applyTo(new Matrix4f()));
            wave.setLineColour(overlayPass
                    ? model.color.toVector4f(1f)
                    : new Vector4f((float) model.index.value, 0f, 0f, 1f));
            wave.doRender(ctx);
        }

        @Override
        public void dispose() {
            wave.dispose();
        }
    }

    private final class SpectrumEntry implements WaveEntry {
        private final SpectrumModel model;
        private SpectrumAnalyser bufferAnalyser;
        private SpectrumAnalyser overlayAnalyser;
        private volatile boolean bufferColourDirty = false;
        private volatile boolean overlayColourDirty = false;

        SpectrumEntry(SpectrumModel model) throws IOException {
            this.model = model;
            bufferAnalyser = buildBuffer();
            overlayAnalyser = buildOverlay();
            audioPipeline.getFreqProc().addSink(bufferAnalyser);
            audioPipeline.getFreqProc().addSink(overlayAnalyser);
            bufferAnalyser.init(initCtx);
            overlayAnalyser.init(initCtx);

            Runnable markBuffer = () -> bufferColourDirty = true;
            model.barColorLow.addChangeListener(markBuffer);
            model.barColorHigh.addChangeListener(markBuffer);
            model.peakColor.addChangeListener(markBuffer);

            Runnable markOverlay = () -> overlayColourDirty = true;
            model.barColorLowRgb.addChangeListener(markOverlay);
            model.barColorHighRgb.addChangeListener(markOverlay);
            model.peakColorRgb.addChangeListener(markOverlay);

            Runnable markBoth = () -> { bufferColourDirty = true; overlayColourDirty = true; };
            model.showBars.addChangeListener(markBoth);
            model.showPeakTicks.addChangeListener(markBoth);
        }

        private SpectrumAnalyser buildBuffer() {
            SpectrumAnalyser sa = new SpectrumAnalyser(audioPipeline.getFreqProc())
                    .withBarColors(model.barColorLowVec(), model.barColorHighVec())
                    .withPeakColor(model.peakColorVec());
            sa.setClearBeforeRender(false);
            return sa;
        }

        private SpectrumAnalyser buildOverlay() {
            SpectrumAnalyser sa = new SpectrumAnalyser(audioPipeline.getFreqProc())
                    .withBarColors(model.barColorLowVecRgb(), model.barColorHighVecRgb())
                    .withPeakColor(model.peakColorVecRgb());
            sa.setClearBeforeRender(false);
            return sa;
        }

        /** GL thread only: disposes and rebuilds the buffer-target analyser with current index colour params. */
        private void reinitBuffer(RenderContext ctx) {
            bufferColourDirty = false;
            audioPipeline.getFreqProc().removeSink(bufferAnalyser);
            bufferAnalyser.dispose();
            bufferAnalyser = buildBuffer();
            audioPipeline.getFreqProc().addSink(bufferAnalyser);
            try {
                bufferAnalyser.init(ctx);
            } catch (IOException e) {
                LOG.error("Failed to reinitialise spectrum analyser after an index colour change", e);
                throw new UncheckedIOException(e);
            }
        }

        /** GL thread only: disposes and rebuilds the overlay-target analyser with current RGB colour params. */
        private void reinitOverlay(RenderContext ctx) {
            overlayColourDirty = false;
            audioPipeline.getFreqProc().removeSink(overlayAnalyser);
            overlayAnalyser.dispose();
            overlayAnalyser = buildOverlay();
            audioPipeline.getFreqProc().addSink(overlayAnalyser);
            try {
                overlayAnalyser.init(ctx);
            } catch (IOException e) {
                LOG.error("Failed to reinitialise spectrum analyser after an RGB colour change", e);
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public RenderMode mode() {
            return model.mode.getEnumeration();
        }

        @Override
        public void render(RenderContext ctx, boolean overlayPass) {
            if (overlayPass) {
                if (overlayColourDirty) reinitOverlay(ctx);
            } else {
                if (bufferColourDirty) reinitBuffer(ctx);
            }
            if (!model.enabled.value) return;
            SpectrumAnalyser active = overlayPass ? overlayAnalyser : bufferAnalyser;
            active.setTransform(model.transform.applyTo(positionBase(model.position.getEnumeration())));
            active.doRender(ctx);
        }

        @Override
        public void dispose() {
            audioPipeline.getFreqProc().removeSink(bufferAnalyser);
            bufferAnalyser.dispose();
            audioPipeline.getFreqProc().removeSink(overlayAnalyser);
            overlayAnalyser.dispose();
        }
    }

    private final class RadialSpectrumEntry implements WaveEntry {
        private final RadialSpectrumModel model;
        private RadialSpectrumAnalyser bufferAnalyser;
        private RadialSpectrumAnalyser overlayAnalyser;
        private volatile boolean bufferColourDirty = false;
        private volatile boolean overlayColourDirty = false;

        RadialSpectrumEntry(RadialSpectrumModel model) throws IOException {
            this.model = model;
            bufferAnalyser = buildBuffer();
            overlayAnalyser = buildOverlay();
            audioPipeline.getFreqProc().addSink(bufferAnalyser);
            audioPipeline.getFreqProc().addSink(overlayAnalyser);
            bufferAnalyser.init(initCtx);
            overlayAnalyser.init(initCtx);

            Runnable markBuffer = () -> bufferColourDirty = true;
            model.innerColor.addChangeListener(markBuffer);
            model.baseColor.addChangeListener(markBuffer);
            model.outerColor.addChangeListener(markBuffer);
            model.peakColor.addChangeListener(markBuffer);

            Runnable markOverlay = () -> overlayColourDirty = true;
            model.innerColorRgb.addChangeListener(markOverlay);
            model.baseColorRgb.addChangeListener(markOverlay);
            model.outerColorRgb.addChangeListener(markOverlay);
            model.peakColorRgb.addChangeListener(markOverlay);

            Runnable markBoth = () -> { bufferColourDirty = true; overlayColourDirty = true; };
            model.showBars.addChangeListener(markBoth);
            model.showPeakTicks.addChangeListener(markBoth);
        }

        private RadialSpectrumAnalyser buildBuffer() {
            RadialSpectrumAnalyser rsa = new RadialSpectrumAnalyser(audioPipeline.getFreqProc())
                    .withColors(model.innerColorVec(), model.baseColorVec(), model.outerColorVec())
                    .withPeakColor(model.peakColorVec());
            rsa.setClearBeforeRender(false);
            return rsa;
        }

        private RadialSpectrumAnalyser buildOverlay() {
            RadialSpectrumAnalyser rsa = new RadialSpectrumAnalyser(audioPipeline.getFreqProc())
                    .withColors(model.innerColorVecRgb(), model.baseColorVecRgb(), model.outerColorVecRgb())
                    .withPeakColor(model.peakColorVecRgb());
            rsa.setClearBeforeRender(false);
            return rsa;
        }

        /** GL thread only: disposes and rebuilds the buffer-target analyser with current index colour params. */
        private void reinitBuffer(RenderContext ctx) {
            bufferColourDirty = false;
            audioPipeline.getFreqProc().removeSink(bufferAnalyser);
            bufferAnalyser.dispose();
            bufferAnalyser = buildBuffer();
            audioPipeline.getFreqProc().addSink(bufferAnalyser);
            try {
                bufferAnalyser.init(ctx);
            } catch (IOException e) {
                LOG.error("Failed to reinitialise radial spectrum analyser after an index colour change", e);
                throw new UncheckedIOException(e);
            }
        }

        /** GL thread only: disposes and rebuilds the overlay-target analyser with current RGB colour params. */
        private void reinitOverlay(RenderContext ctx) {
            overlayColourDirty = false;
            audioPipeline.getFreqProc().removeSink(overlayAnalyser);
            overlayAnalyser.dispose();
            overlayAnalyser = buildOverlay();
            audioPipeline.getFreqProc().addSink(overlayAnalyser);
            try {
                overlayAnalyser.init(ctx);
            } catch (IOException e) {
                LOG.error("Failed to reinitialise radial spectrum analyser after an RGB colour change", e);
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public RenderMode mode() {
            return model.mode.getEnumeration();
        }

        @Override
        public void render(RenderContext ctx, boolean overlayPass) {
            if (overlayPass) {
                if (overlayColourDirty) reinitOverlay(ctx);
            } else {
                if (bufferColourDirty) reinitBuffer(ctx);
            }
            if (!model.enabled.value) return;
            RadialSpectrumAnalyser active = overlayPass ? overlayAnalyser : bufferAnalyser;
            active.withRepeats(model.repeats.value);
            active.setTransform(model.transform.applyTo(new Matrix4f()));
            active.doRender(ctx);
        }

        @Override
        public void dispose() {
            audioPipeline.getFreqProc().removeSink(bufferAnalyser);
            bufferAnalyser.dispose();
            audioPipeline.getFreqProc().removeSink(overlayAnalyser);
            overlayAnalyser.dispose();
        }
    }

    private final class RadialClockEntry implements WaveEntry {
        private final RadialClockModel model;
        private RadialClockAnalyser bufferAnalyser;
        private RadialClockAnalyser overlayAnalyser;
        private volatile boolean bufferDirty = false;
        private volatile boolean overlayDirty = false;

        RadialClockEntry(RadialClockModel model) throws IOException {
            this.model = model;
            bufferAnalyser = buildBuffer();
            overlayAnalyser = buildOverlay();
            audioPipeline.getFreqProc().addSink(bufferAnalyser);
            audioPipeline.getFreqProc().addSink(overlayAnalyser);
            bufferAnalyser.init(initCtx);
            overlayAnalyser.init(initCtx);

            Runnable markBuffer = () -> bufferDirty = true;
            model.innerColor.addChangeListener(markBuffer);
            model.baseColor.addChangeListener(markBuffer);
            model.outerColor.addChangeListener(markBuffer);

            Runnable markOverlay = () -> overlayDirty = true;
            model.innerColorRgb.addChangeListener(markOverlay);
            model.baseColorRgb.addChangeListener(markOverlay);
            model.outerColorRgb.addChangeListener(markOverlay);

            // Geometry is baked into both analysers' constructors alike (it isn't mode-dependent
            // the way colour is), so a geometry change must rebuild both.
            Runnable markBoth = () -> { bufferDirty = true; overlayDirty = true; };
            model.baseRadius.addChangeListener(markBoth);
            model.outerHeight.addChangeListener(markBoth);
            model.innerDepth.addChangeListener(markBoth);
        }

        private RadialClockAnalyser buildBuffer() {
            RadialClockAnalyser rca = new RadialClockAnalyser(audioPipeline.getFreqProc(),
                    (float) model.baseRadius.value, (float) model.outerHeight.value, (float) model.innerDepth.value)
                    .withColors(model.innerColorVec(), model.baseColorVec(), model.outerColorVec());
            rca.setClearBeforeRender(false);
            return rca;
        }

        private RadialClockAnalyser buildOverlay() {
            RadialClockAnalyser rca = new RadialClockAnalyser(audioPipeline.getFreqProc(),
                    (float) model.baseRadius.value, (float) model.outerHeight.value, (float) model.innerDepth.value)
                    .withColors(model.innerColorVecRgb(), model.baseColorVecRgb(), model.outerColorVecRgb());
            rca.setClearBeforeRender(false);
            return rca;
        }

        /** GL thread only: disposes and rebuilds the buffer-target analyser with current index colour/geometry params. */
        private void reinitBuffer(RenderContext ctx) {
            bufferDirty = false;
            audioPipeline.getFreqProc().removeSink(bufferAnalyser);
            bufferAnalyser.dispose();
            bufferAnalyser = buildBuffer();
            audioPipeline.getFreqProc().addSink(bufferAnalyser);
            try {
                bufferAnalyser.init(ctx);
            } catch (IOException e) {
                LOG.error("Failed to reinitialise radial clock analyser after an index colour/geometry change", e);
                throw new UncheckedIOException(e);
            }
        }

        /** GL thread only: disposes and rebuilds the overlay-target analyser with current RGB colour/geometry params. */
        private void reinitOverlay(RenderContext ctx) {
            overlayDirty = false;
            audioPipeline.getFreqProc().removeSink(overlayAnalyser);
            overlayAnalyser.dispose();
            overlayAnalyser = buildOverlay();
            audioPipeline.getFreqProc().addSink(overlayAnalyser);
            try {
                overlayAnalyser.init(ctx);
            } catch (IOException e) {
                LOG.error("Failed to reinitialise radial clock analyser after an RGB colour/geometry change", e);
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public RenderMode mode() {
            return model.mode.getEnumeration();
        }

        @Override
        public void render(RenderContext ctx, boolean overlayPass) {
            if (overlayPass) {
                if (overlayDirty) reinitOverlay(ctx);
            } else {
                if (bufferDirty) reinitBuffer(ctx);
            }
            if (!model.enabled.value) return;
            RadialClockAnalyser active = overlayPass ? overlayAnalyser : bufferAnalyser;
            active.withGrowthMode(model.growthMode.getEnumeration());
            active.withRepeats(model.repeats.value);
            active.withWidthFraction((float) model.widthFraction.value);
            active.setTransform(model.transform.applyTo(new Matrix4f()));
            active.doRender(ctx);
        }

        @Override
        public void dispose() {
            audioPipeline.getFreqProc().removeSink(bufferAnalyser);
            bufferAnalyser.dispose();
            audioPipeline.getFreqProc().removeSink(overlayAnalyser);
            overlayAnalyser.dispose();
        }
    }
}
