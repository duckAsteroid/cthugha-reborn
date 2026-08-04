package io.github.duckasteroid.cthugha.params;

import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import org.joml.Vector4f;

/**
 * An RGB colour, stored as three normalised {@code [0, 1]} components ({@code r}, {@code g},
 * {@code b}) so it round-trips through the same {@link DoubleParameter} machinery
 * (serialization, animation, screen-config capture) as any other leaf value -- see
 * {@link io.github.duckasteroid.cthugha.params.transform.XYParam} for the equivalent pattern
 * applied to a 2-D point. {@link #withColorControl()} tells the remote UI to render the three
 * components as a single native colour-swatch picker instead of three sliders.
 */
public class ColorParam extends ParamNode {

    public final DoubleParameter r;
    public final DoubleParameter g;
    public final DoubleParameter b;

    /** Creates a colour defaulting to opaque white. */
    public ColorParam(String name) {
        this(name, 1f, 1f, 1f);
    }

    public ColorParam(String name, float r, float g, float b) {
        super(name);
        this.r = new DoubleParameter("R", 0.0, 1.0, r);
        this.g = new DoubleParameter("G", 0.0, 1.0, g);
        this.b = new DoubleParameter("B", 0.0, 1.0, b);
        initChildren(this.r, this.g, this.b);
    }

    /** Returns the current colour as an RGBA vector with the given alpha. */
    public Vector4f toVector4f(float alpha) {
        return new Vector4f((float) r.value, (float) g.value, (float) b.value, alpha);
    }

    /** Registers {@code listener} against all three of {@link #r}, {@link #g}, {@link #b}. */
    public void addChangeListener(Runnable listener) {
        r.addChangeListener(listener);
        g.addChangeListener(listener);
        b.addChangeListener(listener);
    }

    /**
     * Marks this colour as a swatch, so the remote UI renders its R/G/B children as a single
     * native colour picker instead of three separate sliders. Returns {@code this} for fluent
     * construction.
     */
    public ColorParam withColorControl() {
        withUiHint(UiHint.CONTROL_TYPE, UiHint.COLOR);
        return this;
    }
}
