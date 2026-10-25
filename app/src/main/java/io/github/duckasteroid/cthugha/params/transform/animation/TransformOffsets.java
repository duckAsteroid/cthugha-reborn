package io.github.duckasteroid.cthugha.params.transform.animation;

/**
 * The animation packages' combined effect on a transform for one frame, applied by
 * {@code TransformParams.applyTo()} on top of the transform's own values: zoom-like factors
 * multiply, angles and positions add. Reset and refilled every frame. GL thread only.
 */
public final class TransformOffsets {

    /** Multiplies Zoom. */
    public double zoom;
    /** Multiply Stretch X and Y. */
    public double stretchX;
    public double stretchY;
    /** Degrees added to Rotation (about the pivot). */
    public double rotation;
    /** Degrees added to Turn (about the vertical axis) and Tip (about the horizontal axis). */
    public double turn;
    public double tip;
    /** NDC offset added to Translation. */
    public double translateX;
    public double translateY;
    /** Multiplies the perspective camera distance; below 1 is nearer (bigger). */
    public double distance;
    /** Whether a package needs the perspective projection. */
    public boolean threeD;

    public TransformOffsets() {
        reset();
    }

    /** Back to "no effect". */
    public void reset() {
        zoom = 1;
        stretchX = 1;
        stretchY = 1;
        rotation = 0;
        turn = 0;
        tip = 0;
        translateX = 0;
        translateY = 0;
        distance = 1;
        threeD = false;
    }
}
