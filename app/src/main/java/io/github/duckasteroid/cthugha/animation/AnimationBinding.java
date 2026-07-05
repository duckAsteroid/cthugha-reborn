package io.github.duckasteroid.cthugha.animation;

import com.asteroid.duck.opengl.util.timer.Clock;
import com.asteroid.duck.opengl.util.timer.function.WaveFunction;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.AbstractValue;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;

/**
 * Drives a single {@link AbstractValue} parameter using a {@link WaveFunction}.
 *
 * <p>Each frame, {@link #tick()} reads the current {@link #frequency} and {@link #phase} params,
 * evaluates the sine wave, normalises its [-1, 1] output to [0, 1], and pushes the result into
 * the target parameter via {@link AbstractValue#setNormalisedValue(double)}.</p>
 */
public class AnimationBinding extends ParamNode {

    public final BooleanParameter enabled = new BooleanParameter("enabled", true);
    public final DoubleParameter frequency = new DoubleParameter("frequency", 0.01, 10.0, 0.2);
    public final DoubleParameter phase = new DoubleParameter("phase", 0.0, 2 * Math.PI, 0.0);

    private final AbstractValue target;
    private WaveFunction fn;

    public AnimationBinding(String name, AbstractValue target, double frequency) {
        super(name);
        this.target = target;
        this.frequency.value = frequency;
        initFields(getClass());
    }

    void init(Clock clock) {
        fn = new WaveFunction(clock, frequency.value, phase.value);
    }

    void tick() {
        if (!enabled.value || fn == null) {
            target.setControlled(false);
            return;
        }
        target.setControlled(true);
        fn.setFrequency(frequency.value);
        fn.setPhase(phase.value);
        target.setNormalisedValue((fn.value() + 1.0) / 2.0);
    }
}
