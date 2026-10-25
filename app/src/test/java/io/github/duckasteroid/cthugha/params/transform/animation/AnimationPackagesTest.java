package io.github.duckasteroid.cthugha.params.transform.animation;

import io.github.duckasteroid.cthugha.params.DynamicChildList.ChildSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationPackagesTest {

    /** Runs {@code pkg} for {@code seconds} at {@code hz} frames per second; returns the last frame's offsets. */
    private static TransformOffsets run(AnimationPackage pkg, double seconds, int hz) {
        TransformOffsets o = new TransformOffsets();
        int frames = (int) Math.round(seconds * hz);
        for (int i = 0; i < frames; i++) {
            o.reset();
            pkg.apply(o, 1.0 / hz, 1f);
        }
        return o;
    }

    @Test
    void everyPackageMovesTheSameAtAnyRefreshRate() {
        for (AnimationType type : AnimationType.values()) {
            if (type == AnimationType.BEAT_PULSE) continue; // driven by audio, not time
            TransformOffsets at24 = run(type.newInstance("a"), 0.75, 24);
            TransformOffsets at60 = run(type.newInstance("b"), 0.75, 60);
            String t = type.label();
            assertEquals(at24.zoom, at60.zoom, 1e-6, t);
            assertEquals(at24.stretchX, at60.stretchX, 1e-6, t);
            assertEquals(at24.rotation, at60.rotation, 1e-6, t);
            assertEquals(at24.turn, at60.turn, 1e-6, t);
            assertEquals(at24.tip, at60.tip, 1e-6, t);
            assertEquals(at24.translateX, at60.translateX, 1e-6, t);
            assertEquals(at24.translateY, at60.translateY, 1e-6, t);
            assertEquals(at24.distance, at60.distance, 1e-6, t);
        }
    }

    @Test
    void everyPackageHasAnEffectAtFullIntensityAndNoneAtZero() {
        for (AnimationType type : AnimationType.values()) {
            if (type == AnimationType.BEAT_PULSE) continue;
            AnimationPackage pkg = type.newInstance("x");
            TransformOffsets moving = run(pkg, 0.3, 60);
            assertFalse(isIdentity(moving), type.label() + " should move at intensity 1");
            AnimationPackage off = type.newInstance("y");
            off.intensity.setValue(0);
            assertTrue(isIdentity(run(off, 0.3, 60)), type.label() + " should not move at intensity 0");
        }
    }

    private static boolean isIdentity(TransformOffsets o) {
        double eps = 1e-9;
        return Math.abs(o.zoom - 1) < eps && Math.abs(o.stretchX - 1) < eps && Math.abs(o.stretchY - 1) < eps
                && Math.abs(o.rotation % 360) < eps && Math.abs(o.turn % 360) < eps && Math.abs(o.tip % 360) < eps
                && Math.abs(o.translateX) < eps && Math.abs(o.translateY) < eps && Math.abs(o.distance - 1) < eps;
    }

    @Test
    void breatheSwingsEvenlyAroundNormalSize() {
        BuiltInPackages.Breathe breathe = (BuiltInPackages.Breathe) AnimationType.BREATHE.newInstance("b");
        breathe.depth.setValue(1.0);
        breathe.rate.setValue(1.0);
        // A quarter cycle in: the top of the swell, double size.
        assertEquals(2.0, run(breathe, 0.25, 100).zoom, 1e-3);
        // Three quarters: the bottom, half size.
        assertEquals(0.5, run(breathe, 0.5, 100).zoom, 1e-3);
    }

    @Test
    void changingARateDoesNotJumpTheMotion() {
        BuiltInPackages.Breathe breathe = (BuiltInPackages.Breathe) AnimationType.BREATHE.newInstance("b");
        TransformOffsets o = run(breathe, 0.5, 60);
        double before = o.zoom;
        breathe.rate.setValue(3.0);
        o.reset();
        breathe.apply(o, 0, 1f); // no time passes
        assertEquals(before, o.zoom, 1e-12);
    }

    @Test
    void stackedPackagesCombine() {
        AnimationList list = new AnimationList();
        BuiltInPackages.Breathe a = (BuiltInPackages.Breathe) list.add(AnimationType.BREATHE);
        BuiltInPackages.Breathe b = (BuiltInPackages.Breathe) list.add(AnimationType.BREATHE);
        BuiltInPackages.Tumble t1 = (BuiltInPackages.Tumble) list.add(AnimationType.TUMBLE);
        BuiltInPackages.Tumble t2 = (BuiltInPackages.Tumble) list.add(AnimationType.TUMBLE);
        for (BuiltInPackages.Breathe x : List.of(a, b)) { x.depth.setValue(1); x.rate.setValue(1); }
        for (BuiltInPackages.Tumble x : List.of(t1, t2)) { x.angle.setValue(30); x.rate.setValue(1); }
        TransformOffsets o = new TransformOffsets();
        list.apply(o, 0.25, 1f); // a quarter cycle: each at its peak
        assertEquals(4.0, o.zoom, 1e-9);      // 2 × 2: zoom multiplies
        assertEquals(60.0, o.rotation, 1e-9); // 30 + 30: angles add
        assertEquals(List.of("Breathe 1", "Breathe 2", "Tumble 1", "Tumble 2"),
                list.packages().stream().map(AnimationPackage::getName).toList());
    }

    @Test
    void threeDPackagesAskForPerspective() {
        for (AnimationType type : List.of(AnimationType.COIN_SPIN, AnimationType.FLIP, AnimationType.SWING,
                AnimationType.FLY_THROUGH, AnimationType.TUMBLE_3D)) {
            assertTrue(run(type.newInstance("x"), 0.1, 60).threeD, type.label());
        }
        assertFalse(run(AnimationType.BREATHE.newInstance("x"), 0.1, 60).threeD);
    }

    @Test
    void theStackRoundTripsThroughItsSpecs() {
        AnimationList list = new AnimationList();
        list.add(AnimationType.COIN_SPIN);
        list.add(AnimationType.DRIFT);
        list.add(AnimationType.COIN_SPIN);
        List<ChildSpec> specs = list.describe();
        assertEquals(List.of(
                new ChildSpec("Coin Spin 1", "COIN_SPIN", Map.of()),
                new ChildSpec("Drift 1", "DRIFT", Map.of()),
                new ChildSpec("Coin Spin 2", "COIN_SPIN", Map.of())), specs);

        AnimationList other = new AnimationList();
        other.add(AnimationType.JELLY);
        other.recreate(specs);
        assertEquals(specs, other.describe());
        // A later addition doesn't reuse a recreated name.
        assertEquals("Coin Spin 3", other.add(AnimationType.COIN_SPIN).getName());
        assertNotEquals(null, other.getChild("Coin Spin 1").orElse(null));
    }
}
