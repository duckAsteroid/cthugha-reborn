package com.asteroid.duck.opengl.util.blur;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

/**
 * This class creates a convolution kernel for doing gaussian blurs.
 * The concept and the maths for this class comes from the article
 * <a href="https://www.rastergrid.com/blog/2010/09/efficient-gaussian-blur-with-linear-sampling/">
 *   "Efficient Gaussian Blur with Linear Sampling" by RasterGrid.</a>
 *
 * Rather than being a strict gaussian around a given point it just does a single row
 *
 * <p>It's essentially a way to implement Gaussian blur in a two pass filter (X/Y) and using as few
 * texture lookups as possible. By using texel interpolation from GL to do some of the maths for
 * you in hardware.</p>
 *
 * <p>The maths is a bit complex, but the idea is that we can use a pascal triangle to get the
 * coefficients for the gaussian kernel, and then we can eliminate the reflection and take half of
 * the coefficients. We then do the maths to ensure that the sample points (linear interpolation)
 * do the right amount of texel blending for the given weights and distance required.</p>
 */
public class BlurKernel {
    private static final Logger LOG = LoggerFactory.getLogger(BlurKernel.class);

    /**
     * Unnormalised half-kernel sample positions, counting from the centre texel outward.
     * {@code offsets[0]} is always {@code 0.0} (the centre tap); subsequent values are
     * {@code 1.0, 2.0, …} in integer steps before the linear-interpolation optimisation
     * is applied by {@link #getDiscreteSampleKernel()}.
     */
	public final double[] offsets;

    /**
     * Normalised Gaussian weights corresponding to each position in {@link #offsets}.
     * The weights sum to 1.0 across both halves of the symmetric kernel (the centre weight
     * {@code weights[0]} counts once; all others count twice). Pass to the shader via
     * the {@code weights} uniform after converting to {@code float[]} with
     * {@link DiscreteSampleKernel#floatWeights()}.
     */
	public final double[] weights;

    /**
     * The odd kernel size this instance was constructed with (e.g. 13, 29, 65).
     * Equals {@link #offsets}{@code .length} and {@link #weights}{@code .length}.
     */
	public final int size;

	/**
	 * Create a blur kernel of a certain size.
	 * @param size the size of the kernel
	 * @throws IllegalArgumentException if the size &lt; 1 or not odd
	 */
	public BlurKernel(int size) {
		if (size <= 1) throw new IllegalArgumentException("Kernel size must be > 1");
		if (size % 2 == 0) throw new IllegalArgumentException("Kernel size must be odd");

		this.size  = size;
		// get pascals triangle for
		double[] pascal = pascal(size);
		// sum all coefficients
		double sum = Arrays.stream(pascal).sum();
		// normalise coefficients (0-1) (against sum)
		double[] normalisedCoefficients = new double[pascal.length];
		for (int i = 0; i < normalisedCoefficients.length; i++) {
			normalisedCoefficients[i] = pascal[i] / sum;
		}
		// ok now we have normalised coefficients
		// lets eliminate the reflection and take half
		int center = (normalisedCoefficients.length / 2); // rounds down 3/2 = 1, 5/2 = 2 etc.
		this.weights = Arrays.copyOfRange(normalisedCoefficients, center, normalisedCoefficients.length);
		this.offsets = DoubleStream.iterate(0, o -> o + 1).limit(weights.length).toArray();
	}

    /**
     * Collapse adjacent non-centre weight pairs into single linear-interpolation samples,
     * exploiting the GPU's bilinear filter to perform two texture lookups in one instruction.
     *
     * <p>Each pair of consecutive non-centre taps is replaced by a single sample positioned
     * between them at the weighted midpoint. The combined weight is the sum of the pair. If
     * the non-centre count is odd, the last tap is kept as-is. The result is a
     * {@link DiscreteSampleKernel} with roughly half as many taps as this full kernel, ready
     * to be uploaded to the shader as two {@code float[]} uniforms.</p>
     *
     * @return the reduced discrete kernel; always has at least one tap (the centre sample)
     */
	public DiscreteSampleKernel getDiscreteSampleKernel() {
		// Pair up the non-center weights into linear-interpolation samples.
		// If there's an odd number of non-center weights, the last one becomes its own sample.
		int nonCenter = weights.length - 1;
		int pairs = nonCenter / 2;
		int remainder = nonCenter % 2;
		int discreteSize = 1 + pairs + remainder;

		double[] discreteWeights = new double[discreteSize];
		double[] discreteOffsets = new double[discreteSize];

		discreteWeights[0] = weights[0];
		discreteOffsets[0] = 0.0;

		for (int i = 0; i < pairs; i++) {
			// 1, 3, 5 ...
			int t1 = 1 + (i * 2);
			// 2, 4, 6 ...
			int t2 = t1 + 1;
			discreteWeights[i + 1] = weights[t1] + weights[t2];
			discreteOffsets[i + 1] = ((offsets[t1] * weights[t1]) + (offsets[t2] * weights[t2])) /
							discreteWeights[i + 1];
		}

		if (remainder == 1) {
			int last = weights.length - 1;
			discreteWeights[discreteSize - 1] = weights[last];
			discreteOffsets[discreteSize - 1] = offsets[last];
		}

		return new DiscreteSampleKernel(discreteOffsets, discreteWeights);
	}


	/**
	 * Pascal's triangle at row N (1 is first).
	 * <pre>
	 * Row                Values
	 * 1                     1
	 * 2                   1   1
	 * 3                 1   2   1
	 * 4               1   3   3   1
	 * 5             1   4   6   4   1
	 * etc.
	 * </pre>
	 * @param n the row of the triangle to return
	 * @return a set of values for pascals triangle at row N. length = N
	 */
	public static double[] pascal(int n) {
		double[] result = new double[n];
		double C = 1.0;
		for (int i = 1; i <= result.length; i++) {
			result[i - 1] = C;
			C = C * (n - i) / i;
		}
		return result;
	}

	private static IntStream size(String[] args) {
		if (args.length == 2) {
			return IntStream.rangeClosed(parseInt(args[0]).orElse(13), parseInt(args[1]).orElse(13));
		}
		else if (args.length == 0) {
			return IntStream.of(13);
		}
		else {
			return Arrays.stream(args)
							.map(BlurKernel::parseInt)
							.filter(Optional::isPresent)
							.mapToInt(Optional::get);

		}
	}

	private static Optional<Integer> parseInt(String s) {
		try {
			return Optional.of(Integer.parseInt(s));
		} catch (NumberFormatException e) {
			LOG.warn("Invalid argument. Expected an integer, got: {}", s);
		}
		return Optional.empty();
	}

    /**
     * CLI utility that prints ready-to-paste GLSL uniform initialisers for a range of kernel sizes.
     * Run with no arguments for a default size of 13; with two arguments for a range; or with
     * multiple individual sizes as space-separated integers. Output goes to stdout.
     *
     * @param args optional size arguments: none (default 13), {@code "min max"} for a range, or
     *             individual integers
     */
	public static void main(String[] args) {
		final IntStream stream = IntStream.rangeClosed(25,33).filter(i -> i % 2 != 0);
		stream.forEach(size -> {
			try {
				BlurKernel kernel = new BlurKernel(size);
				DiscreteSampleKernel dsk = kernel.getDiscreteSampleKernel();
				LOG.info("// Kernel size {}", size);
				double sum = (Arrays.stream(kernel.weights).skip(1).sum() * 2.0) + kernel.weights[0];
				LOG.info("// sum of weights: {}", sum);
				LOG.info(renderAsUniform("offsets_" + size, dsk.offsets()));
				LOG.info(renderAsUniform("weights_" + size, dsk.weights()));
			}
			catch(Throwable t) {
				LOG.error("// Error at size {}: {}", size, t.getMessage());
			}
		});
	}

	private static String renderAsUniform(String variableName, double[] values) {
		return Arrays.stream(values)
						.mapToObj(d -> String.format("%.14f", d))
						.collect(Collectors.joining(", ",
										"uniform float "+variableName+"["+values.length+"] = float[](",");"));
	}
}
