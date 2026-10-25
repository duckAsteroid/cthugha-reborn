package io.github.duckasteroid.cthugha.params.transform.animation;

import java.util.function.Function;

/** The built-in animation packages, as offered by an {@link AnimationList}'s picker. */
public enum AnimationType {
    BREATHE("Breathe", BuiltInPackages.Breathe::new),
    SPIN("Spin", BuiltInPackages.Spin::new),
    ORBIT("Orbit", BuiltInPackages.Orbit::new),
    DRIFT("Drift", BuiltInPackages.Drift::new),
    JELLY("Jelly", BuiltInPackages.Jelly::new),
    BEAT_PULSE("Beat Pulse", BuiltInPackages.BeatPulse::new),
    TUMBLE("Tumble", BuiltInPackages.Tumble::new),
    COIN_SPIN("Coin Spin", BuiltInPackages.CoinSpin::new),
    FLIP("Flip", BuiltInPackages.Flip::new),
    SWING("Swing", BuiltInPackages.Swing::new),
    FLY_THROUGH("Fly Through", BuiltInPackages.FlyThrough::new),
    TUMBLE_3D("Tumble 3D", BuiltInPackages.Tumble3D::new);

    private final String label;
    private final Function<String, AnimationPackage> factory;

    AnimationType(String label, Function<String, AnimationPackage> factory) {
        this.label = label;
        this.factory = factory;
    }

    public String label() {
        return label;
    }

    /** A new instance of this package named {@code name}. */
    public AnimationPackage newInstance(String name) {
        return factory.apply(name);
    }

    @Override
    public String toString() {
        return label;
    }
}
