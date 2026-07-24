package com.asteroid.duck.opengl.util.audio.simulated;

import java.util.ArrayList;

/**
 * A {@link StereoDataSource} that mixes an arbitrary number of stereo sources by summing their
 * left and right channel amplitudes at each sample point.
 *
 * <p>Extend {@link ArrayList} so that sources can be added, removed, and iterated with standard
 * collection semantics. The mix is a simple additive blend — no normalisation is applied, so the
 * caller should ensure individual source amplitudes are scaled so the combined signal stays within
 * the expected [-1, 1] range.</p>
 */
public class CompositeWaveform extends ArrayList<StereoDataSource> implements StereoDataSource {

    /**
     * Create an empty composite with the given initial capacity.
     *
     * @param initialCapacity the initial capacity of the underlying list; set this to the number
     *                        of sources you expect to add to avoid resizing
     */
    public CompositeWaveform(int initialCapacity) {
        super(initialCapacity);
    }

    /**
     * Add a mono source to the mix at the centre of the stereo field.
     * Convenience wrapper that wraps the source in {@link StaticStereoPositioner#CENTER} before
     * adding it, so both channels receive equal amplitude.
     *
     * @param source the mono waveform generator to add
     */
    public void add(MonoDataSource source) {
        add(StaticStereoPositioner.CENTER.wrap(source));
    }

    @Override
    public double[] sample(double time) {
        double[] sample = new double[2];
        // combine all the waveforms for this sample
        for(StereoDataSource w : this) {
            double[] s = w.sample(time);
            sample[0] += s[0];
            sample[1] += s[1];
        }
        return sample;
    }
}
