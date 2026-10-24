package com.asteroid.duck.opengl.util.wave;

/**
 * Maps a vertex position to an amplitude scale factor for the audio wave.
 *
 * <p>Applied per-vertex during VBO construction: the returned value is stored as the Y
 * component of each vertex and multiplied by the normalised audio sample in the vertex shader.
 * Use {@link #constant} for a flat envelope or {@link #ellipse} to taper the wave to zero at
 * the screen edges so it fits inside an ellipse.</p>
 */
@FunctionalInterface
public interface AmplitudeFunction {

    /**
     * Return the amplitude scale factor for a vertex.
     *
     * @param index vertex index in [0, screenWidth)
     * @param x     normalised x position in [-1, 1)
     * @return amplitude scale factor (typically > 0; 0 silences the vertex)
     */
    float amplitudeAt(int index, float x);

    /**
     * Create an amplitude function that returns the same scale factor for every vertex,
     * producing a flat-topped waveform envelope.
     *
     * @param amplitude the constant scale factor applied to every vertex; {@code 1.0} maps the
     *                  full normalised audio signal to the full screen height
     * @return a constant-envelope {@link AmplitudeFunction}
     */
    static AmplitudeFunction constant(float amplitude) {
        return (i, x) -> amplitude;
    }

    /**
     * Create an amplitude function with an elliptical (sine-arch) envelope that tapers the
     * waveform to zero at both screen edges and peaks at {@code maxAmplitude} in the centre.
     * Achieved via {@code maxAmplitude × sin(π × (x + 1) / 2)}, so the amplitude follows a
     * half-sine arch from 0 at {@code x = -1} through {@code maxAmplitude} at {@code x = 0}
     * back to 0 at {@code x = +1}. This prevents hard visual discontinuities at the edges when
     * the waveform is composited over other renderers.
     *
     * @param maxAmplitude the peak scale factor at the horizontal centre; typically in (0, 1]
     * @return an elliptical-envelope {@link AmplitudeFunction}
     */
    static AmplitudeFunction ellipse(float maxAmplitude) {
        return (i, x) -> maxAmplitude * (float) Math.sin(Math.PI * (x + 1.0) / 2.0);
    }
}
