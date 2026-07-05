package io.github.duckasteroid.cthugha.display.phase;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.renderaction.RenderActionQueue;
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
import io.github.duckasteroid.cthugha.params.ContainerNode;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.action.AbstractAction;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.IOException;

/**
 * Renders all audio-reactive wave visualisations directly into the R8 ping-pong buffer.
 *
 * Wave renderers use R-channel = palette index / 255 so they write palette indices directly
 * into pongTex (which is R8) without needing an RGBA intermediate or a bake pass.
 * Palette index 200 is used as the default wave colour.
 */
public class WavePhase implements RenderPhase {

    /** Palette index 200, normalised to [0,1] for the R8 red channel. */
    private static final float WAVE_IDX = 200f / 255f;
    private static final Vector4f WAVE_COLOUR    = new Vector4f(WAVE_IDX, 0f, 0f, 1f);
    private static final Vector3f SPECTRUM_COLOUR = new Vector3f(WAVE_IDX, 0f, 0f);

    private final JCthugha cthugha;
    private AudioPipeline audioPipeline;
    private AudioWave oscWave;
    private RadialWave radWave;
    private SpectrumAnalyser specAnalyser;
    private RadialSpectrumAnalyser radSpecAnalyser;

    public WavePhase(JCthugha cthugha) {
        this.cthugha = cthugha;
    }

    @Override
    public void init(RenderContext ctx) throws IOException {
        audioPipeline = new AudioPipeline();
        audioPipeline.init(ctx);

        oscWave = new AudioWave(audioPipeline.getPboSink());
        oscWave.setLineColour(WAVE_COLOUR);
        oscWave.setLineWidth(2.0f);
        oscWave.setClearBeforeRender(false);
        oscWave.init(ctx);

        radWave = new RadialWave(audioPipeline.getPboSink());
        radWave.setLineColour(WAVE_COLOUR);
        radWave.setLineWidth(2.0f);
        radWave.setClearBeforeRender(false);
        radWave.init(ctx);

        // withBarColors must be called before init()
        specAnalyser = new SpectrumAnalyser(audioPipeline.getFreqProc())
                .withBarColors(SPECTRUM_COLOUR, SPECTRUM_COLOUR);
        specAnalyser.setClearBeforeRender(false);
        audioPipeline.getFreqProc().addSink(specAnalyser);
        specAnalyser.init(ctx);

        // withColors must be called before init()
        radSpecAnalyser = new RadialSpectrumAnalyser(audioPipeline.getFreqProc())
                .withColors(SPECTRUM_COLOUR, SPECTRUM_COLOUR, SPECTRUM_COLOUR);
        radSpecAnalyser.setClearBeforeRender(false);
        audioPipeline.getFreqProc().addSink(radSpecAnalyser);
        radSpecAnalyser.init(ctx);
    }

    @Override
    public void indexedRender(RenderContext ctx) {
        audioPipeline.update();

        OscilloscopeModel om = cthugha.oscilloscope;
        if (om.enabled.value) {
            float amp = (float) om.amplitude.value;
            oscWave.setAmplitudeFunction(
                    om.ellipse.value ? AmplitudeFunction.ellipse(amp) : AmplitudeFunction.constant(amp));
            oscWave.setTransform(om.transform.applyTo(new Matrix4f()));
            oscWave.doRender(ctx);
        }

        RadialWaveModel rm = cthugha.radialWave;
        if (rm.enabled.value) {
            float amp = (float) rm.amplitude.value;
            radWave.setAmplitudeFunction(
                    rm.ellipse.value ? AmplitudeFunction.ellipse(amp) : AmplitudeFunction.constant(amp));
            radWave.setTransform(rm.transform.applyTo(new Matrix4f()));
            radWave.doRender(ctx);
        }

        SpectrumModel sm = cthugha.spectrum;
        if (sm.enabled.value) {
            specAnalyser.setTransform(sm.transform.applyTo(new Matrix4f()));
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
    public void registerActions(ContainerNode generalGroup, RenderActionQueue renderActions) {
        AbstractAction cycleAudio = new AbstractAction("Cycle Audio",
                ctx -> cthugha.notify("audio: " + cycleAudioSource()));
        cycleAudio.withUiHint(UiHint.ICON, "mic");
        generalGroup.addChild(cycleAudio);
    }

    @Override
    public void dispose() {
        if (oscWave         != null) oscWave.dispose();
        if (radWave         != null) radWave.dispose();
        if (specAnalyser    != null) specAnalyser.dispose();
        if (radSpecAnalyser != null) radSpecAnalyser.dispose();
        if (audioPipeline   != null) audioPipeline.dispose();
    }

    public String cycleAudioSource() {
        return audioPipeline.cycleSource().getName();
    }
}
