package io.github.duckasteroid.cthugha.params.transform.animation;

import com.asteroid.duck.opengl.util.audio.analysis.FrequencyBand;
import io.github.duckasteroid.cthugha.binding.ScriptHelpers;
import io.github.duckasteroid.cthugha.display.BeatDetectorConfig;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.EnumParameter;

import java.util.List;

/** The built-in {@link AnimationPackage}s, one per {@link AnimationType}. */
final class BuiltInPackages {

    private BuiltInPackages() {}

    private static DoubleParameter dial(String name, double min, double max, double value, String description) {
        DoubleParameter p = new DoubleParameter(name, min, max, value);
        p.withDescription(description);
        return p;
    }

    /** Grows and shrinks evenly around normal size. */
    static final class Breathe extends AnimationPackage {
        final DoubleParameter depth = dial("Depth", 0, 1, 0.3, "How far it swells and shrinks: 1 is half to double size.");
        final DoubleParameter rate = rate(0.02, 4, 0.25);
        private double phase;

        Breathe(String name) {
            super(name, AnimationType.BREATHE);
            initDials(depth, rate);
        }

        @Override
        protected void apply(TransformOffsets o, double dt, float aspect, double k) {
            phase = advance(phase, rate.value * dt);
            o.zoom *= Math.pow(2, k * depth.value * wave(phase));
        }
    }

    /** Turns continuously, optionally wobbling in speed. */
    static final class Spin extends AnimationPackage {
        static final double WOBBLE_RATE = 0.2;
        final DoubleParameter speed = dial("Speed", -5, 5, 0.25, "Revolutions per second; negative turns anticlockwise.");
        final DoubleParameter wobble = dial("Wobble", 0, 1, 0, "How much the speed surges and slows (1 = stops and doubles).");
        private double angle;
        private double wobblePhase;

        Spin(String name) {
            super(name, AnimationType.SPIN);
            initDials(speed, wobble);
        }

        @Override
        protected void apply(TransformOffsets o, double dt, float aspect, double k) {
            wobblePhase = advance(wobblePhase, WOBBLE_RATE * dt);
            angle = turn(angle, 360 * k * speed.value * (1 + wobble.value * wave(wobblePhase)) * dt);
            o.rotation += angle;
        }
    }

    /** Circles around a point. */
    static final class Orbit extends AnimationPackage {
        final DoubleParameter radius = dial("Radius", 0, 1, 0.3, "Radius of the circle, in screen heights / 2.");
        final DoubleParameter speed = dial("Speed", -2, 2, 0.1, "Laps per second; negative goes anticlockwise.");
        private double phase;

        Orbit(String name) {
            super(name, AnimationType.ORBIT);
            initDials(radius, speed);
        }

        @Override
        protected void apply(TransformOffsets o, double dt, float aspect, double k) {
            phase = advance(phase, speed.value * dt);
            double r = k * radius.value;
            o.translateX += r * Math.cos(2 * Math.PI * phase) / aspect;
            o.translateY += r * Math.sin(2 * Math.PI * phase);
        }
    }

    /** Wanders slowly around the screen in a looping figure. */
    static final class Drift extends AnimationPackage {
        /** Y runs at this multiple of X's rate, so the path doesn't close quickly. */
        static final double Y_RATIO = 1.618;
        final DoubleParameter distance = dial("Distance", 0, 1, 0.3, "How far it wanders from its position.");
        final DoubleParameter rate = rate(0.005, 1, 0.05);
        private double phaseX;
        private double phaseY;

        Drift(String name) {
            super(name, AnimationType.DRIFT);
            initDials(distance, rate);
        }

        @Override
        protected void apply(TransformOffsets o, double dt, float aspect, double k) {
            phaseX = advance(phaseX, rate.value * dt);
            phaseY = advance(phaseY, rate.value * Y_RATIO * dt);
            double d = k * distance.value;
            o.translateX += d * wave(phaseX) / aspect;
            o.translateY += d * wave(phaseY + 0.1);
        }
    }

    /** Squashes and stretches like jelly. */
    static final class Jelly extends AnimationPackage {
        final DoubleParameter amount = dial("Amount", 0, 1, 0.2, "How far it squashes: 1 is half width at double height.");
        final DoubleParameter rate = rate(0.05, 8, 1);
        private double phase;

        Jelly(String name) {
            super(name, AnimationType.JELLY);
            initDials(amount, rate);
        }

        @Override
        protected void apply(TransformOffsets o, double dt, float aspect, double k) {
            phase = advance(phase, rate.value * dt);
            double s = k * amount.value * wave(phase);
            o.stretchX *= Math.pow(2, s);
            o.stretchY *= Math.pow(2, -s);
        }
    }

    /** Kicks bigger on each beat in a band, then settles as the beat fades. */
    static final class BeatPulse extends AnimationPackage {
        final DoubleParameter strength = dial("Strength", 0, 2, 0.5, "Extra size on a full-strength beat (0.5 = half as big again).");
        final EnumParameter<String> band = new EnumParameter<>("Band", bandNames());

        BeatPulse(String name) {
            super(name, AnimationType.BEAT_PULSE);
            band.withDescription("Beat-detection band to follow, from [beats] in cthugha.ini.");
            initDials(strength, band);
        }

        private static List<String> bandNames() {
            List<String> names = BeatDetectorConfig.load().stream().map(FrequencyBand::name).toList();
            return names.isEmpty() ? List.of("bass") : names;
        }

        @Override
        protected void apply(TransformOffsets o, double dt, float aspect, double k) {
            o.zoom *= 1 + k * strength.value * ScriptHelpers.beatStrength(band.getEnumeration());
        }
    }

    /** Rocks back and forth like a pendulum. */
    static final class Tumble extends AnimationPackage {
        final DoubleParameter angle = dial("Angle", 0, 180, 30, "Degrees either side.");
        final DoubleParameter rate = rate(0.02, 4, 0.2);
        private double phase;

        Tumble(String name) {
            super(name, AnimationType.TUMBLE);
            initDials(angle, rate);
        }

        @Override
        protected void apply(TransformOffsets o, double dt, float aspect, double k) {
            phase = advance(phase, rate.value * dt);
            o.rotation += k * angle.value * wave(phase);
        }
    }

    /** Spins round its vertical axis, going edge-on and back. */
    static final class CoinSpin extends AnimationPackage {
        final DoubleParameter speed = dial("Speed", -5, 5, 0.25, "Revolutions per second.");
        private double angle;

        CoinSpin(String name) {
            super(name, AnimationType.COIN_SPIN);
            initDials(speed);
        }

        @Override
        protected void apply(TransformOffsets o, double dt, float aspect, double k) {
            angle = turn(angle, 360 * k * speed.value * dt);
            o.turn += angle;
            o.threeD = true;
        }
    }

    /** Tumbles end over end. */
    static final class Flip extends AnimationPackage {
        final DoubleParameter speed = dial("Speed", -5, 5, 0.25, "Revolutions per second.");
        private double angle;

        Flip(String name) {
            super(name, AnimationType.FLIP);
            initDials(speed);
        }

        @Override
        protected void apply(TransformOffsets o, double dt, float aspect, double k) {
            angle = turn(angle, 360 * k * speed.value * dt);
            o.tip += angle;
            o.threeD = true;
        }
    }

    /** Swings to and fro in depth, like a door or pendulum. */
    static final class Swing extends AnimationPackage {
        final DoubleParameter angle = dial("Angle", 0, 90, 45, "Degrees either side, in depth.");
        final DoubleParameter rate = rate(0.02, 4, 0.25);
        private double phase;

        Swing(String name) {
            super(name, AnimationType.SWING);
            initDials(angle, rate);
        }

        @Override
        protected void apply(TransformOffsets o, double dt, float aspect, double k) {
            phase = advance(phase, rate.value * dt);
            o.turn += k * angle.value * wave(phase);
            o.threeD = true;
        }
    }

    /** Rushes towards the viewer from far away, then loops. */
    static final class FlyThrough extends AnimationPackage {
        final DoubleParameter depth = dial("Depth", 0, 1, 1, "How far it travels: 1 is from a quarter size to four times.");
        final DoubleParameter rate = rate(0.02, 2, 0.25);
        private double phase;

        FlyThrough(String name) {
            super(name, AnimationType.FLY_THROUGH);
            initDials(depth, rate);
        }

        @Override
        protected void apply(TransformOffsets o, double dt, float aspect, double k) {
            phase = advance(phase, rate.value * dt);
            o.distance *= Math.pow(4, k * depth.value * (1 - 2 * phase));
            o.threeD = true;
        }
    }

    /** Rotates on both depth axes at different rates for an irregular tumble. */
    static final class Tumble3D extends AnimationPackage {
        final DoubleParameter speed = dial("Speed", -5, 5, 0.2, "Revolutions per second about the vertical axis.");
        final DoubleParameter twist = dial("Twist", 0.1, 3, 1.618, "Tip speed as a multiple of Speed; non-whole values never repeat exactly.");
        private double turnAngle;
        private double tipAngle;

        Tumble3D(String name) {
            super(name, AnimationType.TUMBLE_3D);
            initDials(speed, twist);
        }

        @Override
        protected void apply(TransformOffsets o, double dt, float aspect, double k) {
            turnAngle = turn(turnAngle, 360 * k * speed.value * dt);
            tipAngle = turn(tipAngle, 360 * k * speed.value * twist.value * dt);
            o.turn += turnAngle;
            o.tip += tipAngle;
            o.threeD = true;
        }
    }
}
