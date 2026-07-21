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
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Renders all audio-reactive wave visualisations directly into the R16 indexed buffer.
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
 * runtime colour setter). To keep those params live-editable from the remote UI, this phase
 * disposes and rebuilds the affected analyser on the render thread whenever a colour or
 * show/hide param changes, instead of only applying them once at startup.
 */
public class WavePhase implements RenderPhase {

    private static final Logger LOG = LoggerFactory.getLogger(WavePhase.class);

    private final JCthugha cthugha;
    private AudioPipeline audioPipeline;
    private AudioWave oscWave;
    private RadialWave radWave;
    private SpectrumAnalyser specAnalyser;
    private RadialSpectrumAnalyser radSpecAnalyser;

    private volatile boolean spectrumColourDirty = false;
    private volatile boolean radialColourDirty = false;

    public WavePhase(JCthugha cthugha) {
        this.cthugha = cthugha;
    }

    @Override
    public void init(RenderContext ctx) throws IOException {
        // pos = index / paletteSize so PaletteRenderer resolves pixelIndex = pos * totalEntries = index
        float waveIdx = 1.0f; //200f / cthugha.paletteMap.size();
        Vector4f waveColour = new Vector4f(waveIdx, 0f, 0f, 1f);

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

        oscWave = new AudioWave(audioPipeline.getPboSink());
        oscWave.setLineColour(waveColour);
        oscWave.setClearBeforeRender(false);
        oscWave.init(ctx);

        radWave = new RadialWave(audioPipeline.getPboSink());
        radWave.setLineColour(waveColour);
        radWave.setClearBeforeRender(false);
        radWave.init(ctx);

        specAnalyser = buildSpectrumAnalyser(cthugha.spectrum);
        audioPipeline.getFreqProc().addSink(specAnalyser);
        specAnalyser.init(ctx);

        radSpecAnalyser = buildRadialSpectrumAnalyser(cthugha.radialSpectrum);
        audioPipeline.getFreqProc().addSink(radSpecAnalyser);
        radSpecAnalyser.init(ctx);

        registerColourListeners();
    }

    /** Marks the linear spectrum analyser for rebuild on the next frame. */
    private void onSpectrumColourChange() {
        spectrumColourDirty = true;
    }

    /** Marks the radial spectrum analyser for rebuild on the next frame. */
    private void onRadialColourChange() {
        radialColourDirty = true;
    }

    private void registerColourListeners() {
        SpectrumModel sm = cthugha.spectrum;
        sm.barColorLow.addChangeListener(this::onSpectrumColourChange);
        sm.barColorHigh.addChangeListener(this::onSpectrumColourChange);
        sm.peakColor.addChangeListener(this::onSpectrumColourChange);
        sm.showBars.addChangeListener(this::onSpectrumColourChange);
        sm.showPeakTicks.addChangeListener(this::onSpectrumColourChange);

        RadialSpectrumModel rsm = cthugha.radialSpectrum;
        rsm.innerColor.addChangeListener(this::onRadialColourChange);
        rsm.baseColor.addChangeListener(this::onRadialColourChange);
        rsm.outerColor.addChangeListener(this::onRadialColourChange);
        rsm.peakColor.addChangeListener(this::onRadialColourChange);
        rsm.showBars.addChangeListener(this::onRadialColourChange);
        rsm.showPeakTicks.addChangeListener(this::onRadialColourChange);
    }

    private SpectrumAnalyser buildSpectrumAnalyser(SpectrumModel sm) {
        SpectrumAnalyser sa = new SpectrumAnalyser(audioPipeline.getFreqProc())
                .withBarColors(sm.barColorLowVec(), sm.barColorHighVec())
                .withPeakColor(sm.peakColorVec());
        sa.setClearBeforeRender(false);
        return sa;
    }

    private RadialSpectrumAnalyser buildRadialSpectrumAnalyser(RadialSpectrumModel rsm) {
        RadialSpectrumAnalyser rsa = new RadialSpectrumAnalyser(audioPipeline.getFreqProc())
                .withColors(rsm.innerColorVec(), rsm.baseColorVec(), rsm.outerColorVec())
                .withPeakColor(rsm.peakColorVec());
        rsa.setClearBeforeRender(false);
        return rsa;
    }

    /** GL thread only: disposes and rebuilds the linear spectrum analyser with current colour params. */
    private void reinitSpectrumAnalyser(RenderContext ctx) {
        spectrumColourDirty = false;
        audioPipeline.getFreqProc().removeSink(specAnalyser);
        specAnalyser.dispose();
        specAnalyser = buildSpectrumAnalyser(cthugha.spectrum);
        audioPipeline.getFreqProc().addSink(specAnalyser);
        try {
            specAnalyser.init(ctx);
        } catch (IOException e) {
            LOG.error("Failed to reinitialise spectrum analyser after a colour change", e);
            throw new UncheckedIOException(e);
        }
    }

    /** GL thread only: disposes and rebuilds the radial spectrum analyser with current colour params. */
    private void reinitRadialSpectrumAnalyser(RenderContext ctx) {
        radialColourDirty = false;
        audioPipeline.getFreqProc().removeSink(radSpecAnalyser);
        radSpecAnalyser.dispose();
        radSpecAnalyser = buildRadialSpectrumAnalyser(cthugha.radialSpectrum);
        audioPipeline.getFreqProc().addSink(radSpecAnalyser);
        try {
            radSpecAnalyser.init(ctx);
        } catch (IOException e) {
            LOG.error("Failed to reinitialise radial spectrum analyser after a colour change", e);
            throw new UncheckedIOException(e);
        }
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

        if (spectrumColourDirty) {
            reinitSpectrumAnalyser(ctx);
        }
        if (radialColourDirty) {
            reinitRadialSpectrumAnalyser(ctx);
        }

        OscilloscopeModel om = cthugha.oscilloscope;
        if (om.enabled.value) {
            float amp = (float) om.amplitude.value;
            oscWave.setLineWidth((float) om.lineWidth.value);
            oscWave.setChannelMode(om.channelMode.getEnumeration().ordinal());
            oscWave.setAmplitudeFunction(
                    om.ellipse.value ? AmplitudeFunction.ellipse(amp) : AmplitudeFunction.constant(amp));
            oscWave.setTransform(om.transform.applyTo(new Matrix4f()));
            oscWave.doRender(ctx);
        }

        RadialWaveModel rm = cthugha.radialWave;
        if (rm.enabled.value) {
            float amp = (float) rm.amplitude.value;
            radWave.setLineWidth((float) rm.lineWidth.value);
            radWave.setChannelMode(rm.channelMode.getEnumeration().ordinal());
            radWave.setAmplitudeFunction(
                    rm.ellipse.value ? AmplitudeFunction.ellipse(amp) : AmplitudeFunction.constant(amp));
            radWave.setTransform(rm.transform.applyTo(new Matrix4f()));
            radWave.doRender(ctx);
        }

        SpectrumModel sm = cthugha.spectrum;
        if (sm.enabled.value) {
            specAnalyser.setTransform(sm.transform.applyTo(positionBase(sm.position.getEnumeration())));
            specAnalyser.doRender(ctx);
        }

        RadialSpectrumModel rsm = cthugha.radialSpectrum;
        if (rsm.enabled.value) {
            radSpecAnalyser.withRepeats(rsm.repeats.value);
            radSpecAnalyser.setTransform(rsm.transform.applyTo(new Matrix4f()));
            radSpecAnalyser.doRender(ctx);
        }
    }

    @Override
    public void dispose() {
        if (oscWave         != null) oscWave.dispose();
        if (radWave         != null) radWave.dispose();
        if (specAnalyser    != null) specAnalyser.dispose();
        if (radSpecAnalyser != null) radSpecAnalyser.dispose();
        if (audioPipeline   != null) audioPipeline.dispose();
    }
}
