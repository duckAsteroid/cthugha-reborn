package io.github.duckasteroid.cthugha.params.transform.animation;

import io.github.duckasteroid.cthugha.params.Node;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One instance of a built-in animation in a transform's {@link AnimationList}: an ordinary
 * {@link ParamNode} whose dials are leaves, which each frame adds its effect to a
 * {@link TransformOffsets} rather than writing the transform's own values. Every package has an
 * {@link #intensity} (0 = no effect) and keeps its own phase, advanced by elapsed time, so a rate
 * change never jumps the motion.
 */
public abstract class AnimationPackage extends ParamNode {

    /** Scales the whole effect: 0 leaves the transform exactly at its own values. */
    public final DoubleParameter intensity = new DoubleParameter("Intensity", 0, 1, 1);

    private final AnimationType type;

    protected AnimationPackage(String name, AnimationType type) {
        super(name);
        this.type = type;
        intensity.withDescription("How strongly this animation applies: 0 switches it off, 1 is full.");
    }

    /** Sets this package's children: {@link #intensity}, then {@code dials} in display order. */
    protected final void initDials(Node... dials) {
        List<Node> children = new ArrayList<>(dials.length + 1);
        children.add(intensity);
        children.addAll(Arrays.asList(dials));
        initChildren(children);
    }

    public AnimationType type() {
        return type;
    }

    /**
     * Advances this package by {@code dt} seconds and adds its effect to {@code offsets}.
     *
     * @param offsets accumulated effect of the packages so far this frame
     * @param dt      seconds since the previous frame (already capped)
     * @param aspect  viewport width / height, to keep circular motion circular
     */
    void apply(TransformOffsets offsets, double dt, float aspect) {
        apply(offsets, dt, aspect, intensity.value);
    }

    /** As {@link #apply(TransformOffsets, double, float)}, with the current intensity. */
    protected abstract void apply(TransformOffsets offsets, double dt, float aspect, double k);

    /** A rate dial in cycles per second. */
    protected static DoubleParameter rate(double min, double max, double value) {
        DoubleParameter rate = new DoubleParameter("Rate", min, max, value);
        rate.withDescription("Cycles per second.");
        return rate;
    }

    /** {@code phase + cycles}, kept within {@code [0, 1)}. */
    protected static double advance(double phase, double cycles) {
        double p = (phase + cycles) % 1.0;
        return p < 0 ? p + 1.0 : p;
    }

    /** {@code degrees + delta}, kept within {@code [0, 360)}. */
    protected static double turn(double degrees, double delta) {
        double d = (degrees + delta) % 360.0;
        return d < 0 ? d + 360.0 : d;
    }

    /** {@code sin(2π × phase)}. */
    protected static double wave(double phase) {
        return Math.sin(2 * Math.PI * phase);
    }
}
