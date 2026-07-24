package com.asteroid.duck.opengl.util.blur;

/**
 * A reduced set of texture sample positions and their Gaussian weights, optimised for upload
 * to a GLSL blur shader.
 *
 * <p>Produced by {@link BlurKernel#getDiscreteSampleKernel()}. By using {@code GL_LINEAR}
 * texture filtering, two adjacent full-kernel taps can be fetched in a single hardware sample
 * placed at their weighted midpoint, halving the number of texture reads compared to the raw
 * kernel. The shader iterates {@code offsets[1..n-1]} symmetrically around the centre texel
 * (offset 0), weighting each sample by the corresponding entry in {@code weights}.</p>
 *
 * @param offsets fractional texel distances from the centre tap; {@code offsets[0]} is always
 *                {@code 0.0}, subsequent values are increasing non-integer positions that land
 *                between two raw texels so bilinear filtering blends them correctly
 * @param weights normalised Gaussian contribution of each sample; {@code weights[0]} is the
 *                centre weight; all others are applied symmetrically on both sides of centre.
 *                The full sum {@code weights[0] + 2 * sum(weights[1..])} equals 1.0
 */
public record DiscreteSampleKernel(double[] offsets, double[] weights) {
    /**
     * Returns the number of texture samples (taps) this kernel requires per fragment, counting
     * only one side of the symmetric kernel plus the centre tap.
     *
     * @return tap count; equals {@code offsets.length}
     */
	public int size() {
		return offsets.length;
	}

    /**
     * Convert the double-precision {@link #offsets()} array to {@code float[]} for upload to a
     * GLSL {@code uniform float offsets[]} array.
     *
     * @return a new float array of the same length as {@link #offsets()}
     */
	public float[] floatOffsets() {
		float[] result = new float[offsets.length];
		for (int i = 0; i < result.length; i++) {
			result[i] = (float) offsets[i];
		}
		return result;
	}

    /**
     * Convert the double-precision {@link #weights()} array to {@code float[]} for upload to a
     * GLSL {@code uniform float weights[]} array.
     *
     * @return a new float array of the same length as {@link #weights()}
     */
	public float[] floatWeights() {
		float[] result = new float[weights.length];
		for (int i = 0; i < result.length; i++) {
			result[i] = (float) weights[i];
		}
		return result;
	}
}
