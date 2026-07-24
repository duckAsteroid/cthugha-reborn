package com.asteroid.duck.opengl.util.audio.analysis;

import com.asteroid.duck.opengl.util.audio.AudioSink;
import com.asteroid.duck.opengl.util.audio.ChannelMode;
import com.asteroid.duck.opengl.util.audio.RollingAudioBuffer;

import java.util.ArrayList;
import java.util.List;

/**
 * Bridges the raw-audio pipeline to the frequency domain: receives interleaved PCM bytes via
 * {@link AudioSink}, runs a windowed FFT once per frame on the render thread, and fans the
 * normalised magnitude array out to all registered {@link FrequencySink}s.
 *
 * <p>This mirrors the role of {@code AudioReader} in the raw-audio pipeline — one source,
 * multiple consumers — but operates at the frequency level. Any number of
 * {@link FrequencySink}s (e.g. {@link BeatDetector}, {@link com.asteroid.duck.opengl.util.wave.SpectrumAnalyser}) share a single
 * FFT computation with no duplication of work.</p>
 *
 * <h2>Threading model</h2>
 * <ul>
 *   <li>{@link #write} — called from the {@code AudioReader} background thread; safe to call
 *       concurrently with {@link #process}.</li>
 *   <li>{@link #process} — must be called on the render thread exactly once per frame, before
 *       any {@link FrequencySink} that reads the result in the same frame.</li>
 *   <li>{@link #addSink} / {@link #removeSink} — call on the render thread or before the render
 *       loop starts.</li>
 * </ul>
 *
 * <h2>Typical wiring</h2>
 * <pre>{@code
 * FrequencyProcessor freqProc = new FrequencyProcessor(
 *     1024, 128, 48_000f, 20f, 20_000f, -80f, 0f);
 *
 * BeatDetector beats = new BeatDetector(freqProc);
 * freqProc.addSink(beats);
 *
 * SpectrumAnalyser analyser = new SpectrumAnalyser(freqProc);
 * freqProc.addSink(analyser);
 *
 * AudioReader audioReader = new AudioReader(List.of(freqProc));
 * // ... start audioReader thread, call setLine() ...
 *
 * // doRender() — one call, one FFT, all sinks notified:
 * freqProc.process();
 * analyser.doRender(ctx);
 * float kick = beats.getBeatStrength("bass");
 * }</pre>
 */
public class FrequencyProcessor implements AudioSink {

    private final RollingAudioBuffer  audioBuffer;
    private final FFTProcessor        fftProcessor;
    private final ChannelMode         channelMode;
    private final float[]             sampleBuffer;
    private final float[]             magnitudes;
    private final List<FrequencySink> sinks = new ArrayList<>();

    /**
     * Construct a processor with {@link ChannelMode#MONO_BLEND} channel mixing.
     *
     * @param fftSize    FFT window size in samples; must be a power of two (e.g. 1024)
     * @param numBins    number of output frequency bars (e.g. 128)
     * @param sampleRate capture sample rate in Hz (e.g. 48 000)
     * @param fMin       lowest displayed frequency in Hz (e.g. 20)
     * @param fMax       highest displayed frequency in Hz (e.g. 20 000)
     * @param dBFloor    dB level mapped to output 0.0 (e.g. −80)
     * @param dBCeiling  dB level mapped to output 1.0 (e.g. 0)
     */
    public FrequencyProcessor(int fftSize, int numBins, float sampleRate,
                               float fMin, float fMax, float dBFloor, float dBCeiling) {
        this(fftSize, numBins, sampleRate, fMin, fMax, dBFloor, dBCeiling, ChannelMode.MONO_BLEND);
    }

    /**
     * Full constructor with explicit channel mode.
     *
     * @param fftSize     FFT window size in samples; must be a power of two
     * @param numBins     number of output frequency bars
     * @param sampleRate  capture sample rate in Hz
     * @param fMin        lowest displayed frequency in Hz
     * @param fMax        highest displayed frequency in Hz
     * @param dBFloor     dB level mapped to output 0.0
     * @param dBCeiling   dB level mapped to output 1.0
     * @param channelMode which channel(s) to use when converting stereo PCM to mono samples
     */
    public FrequencyProcessor(int fftSize, int numBins, float sampleRate,
                               float fMin, float fMax, float dBFloor, float dBCeiling,
                               ChannelMode channelMode) {
        this.fftProcessor = new FFTProcessor(fftSize, numBins, sampleRate, fMin, fMax, dBFloor, dBCeiling);
        this.audioBuffer  = new RollingAudioBuffer(fftSize * 4);
        this.channelMode  = channelMode;
        this.sampleBuffer = new float[fftSize];
        this.magnitudes   = new float[numBins];
    }

    // ── AudioSink ────────────────────────────────────────────────────────────────

    /**
     * Receive raw PCM bytes from the {@code AudioReader} background thread.
     * Thread-safe with respect to {@link #process}.
     */
    @Override
    public void write(byte[] data, int offset, int length) {
        audioBuffer.write(data, offset, length);
    }

    // ── Render-thread API ─────────────────────────────────────────────────────────

    /**
     * Run the FFT for this frame and dispatch magnitudes to all registered {@link FrequencySink}s.
     *
     * <p>Must be called exactly once per frame on the render thread, before any code in the same
     * frame that reads frequency data (e.g. before {@link com.asteroid.duck.opengl.util.wave.SpectrumAnalyser#doRender(com.asteroid.duck.opengl.util.RenderContext)} or querying
     * {@link BeatDetector#getBeatStrength}).</p>
     *
     * <p>The shared {@code magnitudes} array is passed directly to each sink — sinks must not
     * modify it.</p>
     */
    public void process() {
        audioBuffer.readSamples(sampleBuffer, fftProcessor.getFftSize(), channelMode);
        fftProcessor.process(sampleBuffer, magnitudes);
        for (FrequencySink sink : sinks) {
            sink.onSpectrum(magnitudes);
        }
    }

    /**
     * Register a sink to receive spectrum data on every call to {@link #process}.
     * Must be called on the render thread (or before the render loop starts).
     */
    public void addSink(FrequencySink sink) {
        sinks.add(sink);
    }

    /**
     * Unregister a previously added sink. No-op if the sink was not registered.
     */
    public void removeSink(FrequencySink sink) {
        sinks.remove(sink);
    }

    // ── Parameter accessors ───────────────────────────────────────────────────────

    /** Number of output frequency bars per {@link #process} call; matches {@code numBins} at construction. */
    public int getNumBins() { return fftProcessor.getNumBins(); }

    /** FFT window size in samples; matches {@code fftSize} at construction. */
    public int getFftSize() { return fftProcessor.getFftSize(); }

    /** Lowest frequency in the output range in Hz; matches {@code fMin} at construction. */
    public float getFMin() { return fftProcessor.getFMin(); }

    /** Highest frequency in the output range in Hz; matches {@code fMax} at construction. */
    public float getFMax() { return fftProcessor.getFMax(); }
}
