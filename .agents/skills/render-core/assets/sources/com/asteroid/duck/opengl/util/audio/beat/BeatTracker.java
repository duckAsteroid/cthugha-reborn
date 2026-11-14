package com.asteroid.duck.opengl.util.audio.beat;

/**
 * The full tracker: kicks from a {@link KickDetector}; the tempo from a {@link TempoEstimator}
 * fed by {@link SpectralFlux}, re-estimated every {@value #TEMPO_INTERVAL_SECONDS} s; and beats
 * predicted by a {@link BeatPhaseTracker} locked to the flux and the kicks. Beats are reported
 * at their predicted times as the clock reaches them, so they arrive on time, not late.
 *
 * <p>Reported tempo confidence is the {@link TempoEstimator}'s confidence times the phase
 * tracker's {@link BeatPhaseTracker#lockConfidence() lock confidence}, so it falls through a
 * breakdown and recovers when the beat returns. If the input stays below
 * {@value #SILENCE_DBFS} dBFS for {@value #SILENCE_SECONDS} s, the tracker forgets the tempo and
 * phase — reporting a {@code NaN} tempo with zero confidence, and no more beats — and locks on
 * afresh when sound returns.</p>
 *
 * <p>Spectral flux is the frame-to-frame increase in windowed energy, so it peaks as an onset
 * crosses the steepest part of the Hann window, a quarter of a window in from its newest end:
 * flux frames are timed that far before the end of the window that produced them.</p>
 */
public final class BeatTracker implements BeatAnalyser {

    static final double TEMPO_INTERVAL_SECONDS = 0.5;
    /** Tempo prior: centred on dance-music tempo, a wide octave either way. */
    static final double PRIOR_BPM = 120;
    static final double PRIOR_OCTAVES = 1.0;
    static final double SILENCE_DBFS = -60;
    static final double SILENCE_SECONDS = 1.5;

    private final float sampleRate;
    private final KickDetector kicks;
    private final SpectralFlux flux;
    private final TempoEstimator tempo;
    private final BeatPhaseTracker phase;
    private final BeatListener listener;
    private final int framesPerEstimate;
    private long frames;

    // Silence gate: peak 10 ms mean-square level over the last SILENCE_SECONDS
    private final int levelChunk;
    private final double[] levels;
    private double chunkSum;
    private int chunkCount;
    private long chunks;
    private long samplesConsumed;
    private boolean silent = true;

    /**
     * @param sampleRate sample rate of the audio, in Hz
     * @param listener   receives beats, kicks and tempo estimates
     */
    public BeatTracker(float sampleRate, BeatListener listener) {
        this(sampleRate, listener, PRIOR_BPM, PRIOR_OCTAVES);
    }

    /**
     * @param sampleRate   sample rate of the audio, in Hz
     * @param listener     receives beats, kicks and tempo estimates
     * @param priorBpm     centre of the tempo prior
     * @param priorOctaves width of the tempo prior, in octaves
     */
    public BeatTracker(float sampleRate, BeatListener listener, double priorBpm, double priorOctaves) {
        this.sampleRate = sampleRate;
        this.flux = new SpectralFlux(sampleRate, this::onFrame);
        this.phase = new BeatPhaseTracker(flux.frameRate(), listener);
        this.listener = listener;
        this.kicks = new KickDetector(sampleRate, new BeatListener() {
            @Override
            public void onKick(double time, float strength) {
                listener.onKick(time, strength);
                phase.addKick(time, strength);
            }
        });
        this.tempo = new TempoEstimator(flux.frameRate(), priorBpm, priorOctaves);
        this.framesPerEstimate = (int) Math.round(TEMPO_INTERVAL_SECONDS * flux.frameRate());
        this.levelChunk = Math.max(1, Math.round(sampleRate / 100));
        this.levels = new double[(int) Math.round(SILENCE_SECONDS * 100)];
    }

    private void onFrame(double time, float value) {
        double onsetTime = time - quarterWindow();
        phase.addFrame(onsetTime, value);
        tempo.add(value);
        if (++frames % framesPerEstimate == 0 && !silent && tempo.estimate()) {
            listener.onTempo(time, tempo.bpm(), tempo.confidence() * phase.lockConfidence());
            phase.onTempo(tempo.bpm(), onsetTime);
        }
    }

    /** Update the silence gate; on going silent, forget tempo and phase. */
    private void trackLevel(float[] samples, int offset, int length) {
        for (int i = offset; i < offset + length; i++) {
            chunkSum += samples[i] * samples[i];
            samplesConsumed++;
            if (++chunkCount < levelChunk) continue;
            levels[(int) (chunks++ % levels.length)] = chunkSum / chunkCount;
            chunkSum = 0;
            chunkCount = 0;
            double peak = 0;
            for (double level : levels) peak = Math.max(peak, level);
            boolean nowSilent = chunks >= levels.length && 10 * Math.log10(peak + 1e-20) < SILENCE_DBFS;
            if (nowSilent && !silent) {
                tempo.reset();
                phase.reset();
                listener.onTempo(samplesConsumed / (double) sampleRate, Double.NaN, 0);
            }
            silent = nowSilent;
        }
    }

    private double quarterWindow() {
        return flux.fftSize() / 4.0 / sampleRate;
    }

    /**
     * The beat period the phase tracker is currently running at — the tempo estimate as refined
     * by the phase-locked loop.
     * @return seconds per beat, or {@code NaN} before the first tempo estimate
     */
    public double period() {
        return phase.period();
    }

    @Override
    public float getSampleRate() {
        return sampleRate;
    }

    @Override
    public void process(float[] samples, int offset, int length) {
        trackLevel(samples, offset, length);
        kicks.process(samples, offset, length);
        flux.process(samples, offset, length);
    }
}
