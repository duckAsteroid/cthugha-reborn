package com.asteroid.duck.opengl.util;

import org.joml.Matrix4f;

/**
 * Implemented by renderers that accept a configurable {@link Matrix4f} applied to all output
 * vertex positions before rasterisation.
 *
 * <p>The transform operates in NDC (Normalised Device Coordinate) space, where the origin
 * {@code (0, 0)} is the screen centre. Rotation, scale, and shear therefore all pivot around the
 * screen centre by default. To pivot around a different point, compose the matrix with
 * translate-to-origin / transform / translate-back steps.</p>
 *
 * <p>Implementations must be safe to call from any thread — the matrix should be applied on the
 * render thread at the start of the next frame (e.g. via a {@code RenderActionQueue}).</p>
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * // Slow rotation with beat-driven scale pulse:
 * angle += 0.005f;
 * float pulse = 1.0f + 0.15f * beats.getBeatStrength(0);
 * renderer.setTransform(new Matrix4f().rotateZ(angle).scale(pulse));
 *
 * // Reset to default:
 * renderer.setTransform(new Matrix4f());   // identity
 * }</pre>
 */
public interface Transformable {

    /**
     * Set the transform matrix applied to all output vertex positions.
     * The matrix is applied in NDC space — the pivot is the screen centre {@code (0, 0)}.
     * The supplied matrix is copied internally; the caller may reuse it after this call returns.
     *
     * @param matrix the transform to apply; pass {@code new Matrix4f()} to reset to identity
     */
    void setTransform(Matrix4f matrix);
}
