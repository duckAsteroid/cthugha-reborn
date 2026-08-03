package com.asteroid.duck.opengl.util.audio.simulated;

/**
 * Strategy for distributing a mono audio signal across the stereo image.
 *
 * <p>Implementations convert a single mono sample into a two-element {@code [left, right]}
 * pair by computing per-channel amplitudes from a stereo position value in [−1, +1]:
 * {@code −1} is hard-left, {@code 0} is centre, {@code +1} is hard-right.</p>
 */
public interface StereoPositioner {
    /** Hard-left stereo position; all energy in the left channel. */
    double POSITION_MAX_LEFT = -1.0;
    /** Centre stereo position; equal energy in both channels. */
    double POSITION_CENTER = 0.0;
    /** Hard-right stereo position; all energy in the right channel. */
    double POSITION_MAX_RIGHT = 1.0;

    /**
     * Sample the given mono source and place it in the stereo field at the position
     * appropriate for {@code time}.
     *
     * @param source the mono audio source to read from
     * @param time   elapsed time in seconds, used by time-varying implementations
     * @return a two-element {@code [left, right]} sample array
     */
    default double[] sample(MonoDataSource source, double time) {
        var sample = source.sample(time);
        return sample(sample, time);
    }

    /**
     * Place an already-sampled mono value into the stereo field.
     *
     * @param monoSample the mono amplitude to distribute
     * @param time       elapsed time in seconds, used by time-varying implementations
     * @return a two-element {@code [left, right]} sample array
     */
    double[] sample(double monoSample, double time);

    /**
     * Compute per-channel amplitude coefficients from a stereo position.
     *
     * <p>Uses a linear pan law: left = (1 − pos) / 2, right = (1 + pos) / 2.
     * At centre (0) both channels receive 0.5; at the extremes one channel receives 1.0
     * and the other 0.0.</p>
     *
     * @param stereoPosition position in [−1, +1]
     * @return {@code [leftAmplitude, rightAmplitude]}
     */
    static double[] amplitudes(double stereoPosition) {
        return new double[]{
            (1.0 - stereoPosition) / 2.0,
            (1.0 + stereoPosition) / 2.0
        };
    }

    /**
     * Wrap a {@link MonoDataSource} with this positioner as a {@link StereoDataSource}.
     *
     * @param source the mono source to wrap
     * @return a stereo source that delegates to {@link #sample(MonoDataSource, double)}
     */
    default StereoDataSource wrap(MonoDataSource source) {
        return new StereoDataSource() {
            @Override
            public double[] sample(double time) {
                double channelSample = source.sample(time);
                return StereoPositioner.this.sample(channelSample, time);
            }
        };
    }
}
