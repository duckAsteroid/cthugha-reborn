package com.asteroid.duck.opengl.util.audio.simulated;

import java.util.Objects;

/**
 * Places a MonoDataSource at a fixed position in the stereo field. The position is determined by the stereoPosition
 * parameter, where -1.0 is fully left, +1.0 is fully right, and 0.0 is centered.
 */
public class StaticStereoPositioner implements StereoPositioner {

    // Calculate amplitudes for left and right channels
    private final double leftChannelAmplitude;
    private final double rightChannelAmplitude;

    public static final StaticStereoPositioner CENTER = new StaticStereoPositioner(POSITION_CENTER);

    public StaticStereoPositioner(double stereoPosition) {
        if (stereoPosition < POSITION_MAX_LEFT || stereoPosition > POSITION_MAX_RIGHT) {
            throw new IllegalArgumentException("stereoPosition must be between -1.0 and +1.0.");
        }
        // Calculate amplitudes for left and right channels
        double[] amplitudes = StereoPositioner.amplitudes(stereoPosition);
        this.leftChannelAmplitude = amplitudes[0];
        this.rightChannelAmplitude = amplitudes[1];
    }

    public double[] sample(double channelSample, double time) {
        // Apply amplitudes to your signal sample
        double leftSample = channelSample * leftChannelAmplitude;
        double rightSample = channelSample * rightChannelAmplitude;

        return new double[]{
                leftSample, rightSample
        };
    }

    public StereoDataSource wrap(MonoDataSource source) {
        Objects.requireNonNull(source, "Source must not be null");
        return (time) -> sample(source.sample(time), time);
    }
}
