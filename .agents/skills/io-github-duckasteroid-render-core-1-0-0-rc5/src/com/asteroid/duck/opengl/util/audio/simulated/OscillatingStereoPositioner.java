package com.asteroid.duck.opengl.util.audio.simulated;

/**
 * A {@link StereoPositioner} that sweeps the stereo image continuously from one position to another
 * at a given frequency, producing an auto-panning effect.
 *
 * <p>The position oscillates sinusoidally between {@code stereoPositionA} and {@code stereoPositionB}
 * at {@code frequency} hertz. A position of {@link StereoPositioner#POSITION_MAX_LEFT} ({@code -1})
 * directs all audio to the left channel, {@link StereoPositioner#POSITION_MAX_RIGHT} ({@code +1}) to
 * the right, and {@link StereoPositioner#POSITION_CENTER} ({@code 0}) to both equally.</p>
 */
public class OscillatingStereoPositioner implements StereoPositioner {

    private final double frequency;
    private final double stereoPositionA;
    private final double stereoPositionB;

    /**
     * Create a positioner that sweeps the full width from hard-left to hard-right.
     *
     * @param frequency the sweep rate in hertz; 1.0 completes one left-to-right-to-left cycle per second
     * @return a new positioner configured for maximum stereo sweep
     */
    public static OscillatingStereoPositioner fullScale(double frequency) {
        return new OscillatingStereoPositioner(frequency, StereoPositioner.POSITION_MAX_LEFT, StereoPositioner.POSITION_MAX_RIGHT);
    }

    /**
     * Create an oscillating positioner with explicit endpoints.
     *
     * @param frequency        the sweep rate in hertz
     * @param stereoPositionA  the starting (and half-cycle) stereo position in [−1, +1]
     * @param stereoPositionB  the ending (and other half-cycle) stereo position in [−1, +1]
     */
    public OscillatingStereoPositioner(double frequency, double stereoPositionA, double stereoPositionB) {
        this.frequency = frequency;
        this.stereoPositionA = stereoPositionA;
        this.stereoPositionB = stereoPositionB;
    }

    /**
     * Compute the instantaneous stereo position at the given time using sinusoidal interpolation.
     *
     * @param time elapsed time in seconds
     * @return stereo position in [stereoPositionA, stereoPositionB] oscillating at {@code frequency} Hz
     */
    public double stereoPosition(double time) {
        // Oscillates between 0 and 1
        double oscillation = (Math.sin(2 * Math.PI * frequency * time) + 1.0) / 2.0;
        return stereoPositionA * (1 - oscillation) + stereoPositionB * oscillation;
    }

    @Override
    public double[] sample(double channelSample, double time) {
        var stereoPosition = stereoPosition(time);
        var amplitudes = StereoPositioner.amplitudes(stereoPosition);
        return new double[]{
                channelSample * amplitudes[0],
                channelSample * amplitudes[1]
        };
    }


}
