package com.asteroid.duck.opengl.util.audio.beat;

/**
 * Second-order IIR filter (RBJ Audio EQ Cookbook coefficients), transposed direct form II in
 * double precision. Cascade two for a fourth-order response.
 */
final class Biquad {

    /** Butterworth Q: maximally flat pass band. */
    static final double BUTTERWORTH_Q = Math.sqrt(0.5);

    private final double b0;
    private final double b1;
    private final double b2;
    private final double a1;
    private final double a2;
    private double z1;
    private double z2;

    private Biquad(double b0, double b1, double b2, double a0, double a1, double a2) {
        this.b0 = b0 / a0;
        this.b1 = b1 / a0;
        this.b2 = b2 / a0;
        this.a1 = a1 / a0;
        this.a2 = a2 / a0;
    }

    /**
     * @param cutoff     -3 dB frequency in Hz
     * @param sampleRate sample rate in Hz
     * @return a Butterworth low-pass section
     */
    static Biquad lowPass(double cutoff, double sampleRate) {
        double w0 = 2 * Math.PI * cutoff / sampleRate;
        double cos = Math.cos(w0);
        double alpha = Math.sin(w0) / (2 * BUTTERWORTH_Q);
        return new Biquad((1 - cos) / 2, 1 - cos, (1 - cos) / 2, 1 + alpha, -2 * cos, 1 - alpha);
    }

    /**
     * @param cutoff     -3 dB frequency in Hz
     * @param sampleRate sample rate in Hz
     * @return a Butterworth high-pass section
     */
    static Biquad highPass(double cutoff, double sampleRate) {
        double w0 = 2 * Math.PI * cutoff / sampleRate;
        double cos = Math.cos(w0);
        double alpha = Math.sin(w0) / (2 * BUTTERWORTH_Q);
        return new Biquad((1 + cos) / 2, -(1 + cos), (1 + cos) / 2, 1 + alpha, -2 * cos, 1 - alpha);
    }

    /**
     * @param x the next input sample
     * @return the next output sample
     */
    double process(double x) {
        double y = b0 * x + z1;
        z1 = b1 * x - a1 * y + z2;
        z2 = b2 * x - a2 * y;
        return y;
    }
}
