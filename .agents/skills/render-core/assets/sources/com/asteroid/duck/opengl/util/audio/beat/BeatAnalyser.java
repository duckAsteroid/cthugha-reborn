package com.asteroid.duck.opengl.util.audio.beat;

/**
 * Consumes a stream of mono audio samples and reports beats, kicks and tempo estimates to a
 * {@link BeatListener} as it goes.
 *
 * <p>Time is measured in samples: after {@code n} samples have been passed to
 * {@link #process}, the analyser's clock reads {@code n / getSampleRate()} seconds, and every
 * event it reports carries a time on that clock. Nothing reads the wall clock, so feeding a
 * file faster than real time gives exactly the events a live run would.</p>
 *
 * <p>Implementations are single-threaded: call {@link #process} from one thread only.</p>
 */
public interface BeatAnalyser {

    /**
     * Sample rate of the audio passed to {@link #process}.
     * @return sample rate in Hz
     */
    float getSampleRate();

    /**
     * Consume the next block of samples. The analyser's clock advances by
     * {@code length / getSampleRate()} seconds; any events found are reported to the listener
     * before this method returns.
     * @param samples mono samples in {@code [-1, 1]}, oldest first
     * @param offset  index of the first sample to consume
     * @param length  number of samples to consume
     */
    void process(float[] samples, int offset, int length);
}
