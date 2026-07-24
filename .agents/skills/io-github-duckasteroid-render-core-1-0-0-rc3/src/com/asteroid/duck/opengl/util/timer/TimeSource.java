package com.asteroid.duck.opengl.util.timer;

import org.lwjgl.glfw.GLFW;

import java.util.concurrent.Callable;

/**
 * A named {@link Callable}{@code <Double>} that supplies raw timestamps in seconds, used as
 * the backing time source for {@link ClockImpl}.
 *
 * <p>The name is purely informational and appears in {@link #toString()} for logging purposes.
 * Use the static factory methods to obtain the standard time sources:</p>
 * <ul>
 *   <li>{@link #glfwGetTimeInstance()} — GLFW's high-resolution timer; preferred in the render loop</li>
 *   <li>{@link #systemCurrentTimeMillisInstance()} — {@code System.currentTimeMillis()} converted to seconds</li>
 *   <li>{@link #systemNanoTimeInstance()} — {@code System.nanoTime()} converted to seconds</li>
 * </ul>
 */
public class TimeSource implements Callable<Double> {
    private final String name;
    private final Callable<Double> function;

    /**
     * Create a named time source wrapping the given function.
     *
     * @param name     human-readable label used in {@link #toString()}
     * @param function the underlying timestamp supplier; called on each {@link #call()}
     */
    public TimeSource(String name, Callable<Double> function) {
        this.name = name;
        this.function = function;
    }

    /**
     * Returns the current timestamp in seconds by delegating to the wrapped function.
     *
     * @return current timestamp in seconds
     * @throws Exception if the wrapped function throws
     */
    @Override
    public Double call() throws Exception {
        return function.call();
    }

    /**
     * Returns the human-readable name of this time source.
     *
     * @return the name supplied at construction
     */
    @Override
    public String toString() {
        return name;
    }

    /**
     * Returns a time source backed by {@link GLFW#glfwGetTime()}.
     * This is the preferred source for the render loop as it uses a high-resolution monotonic
     * timer with sub-millisecond precision.
     *
     * @return a {@link Callable} that returns {@code glfwGetTime()} in seconds
     */
    public static Callable<Double> glfwGetTimeInstance() {
        return new TimeSource("GLFW::glfwGetTime", GLFW::glfwGetTime);
    }

    /**
     * Returns a time source backed by {@link System#currentTimeMillis()}, converted to seconds.
     *
     * @return a {@link Callable} that returns the current epoch time in seconds
     */
    public static Callable<Double> systemCurrentTimeMillisInstance() {
        return new TimeSource("System.currentTimeMillis()", () -> System.currentTimeMillis() / 1000.0);
    }

    /**
     * Returns a time source backed by {@link System#nanoTime()}, converted to seconds.
     * Suitable for use outside of a GLFW context (e.g. unit tests or standalone tools).
     *
     * @return a {@link Callable} that returns {@code System.nanoTime()} in seconds
     */
    public static Callable<Double> systemNanoTimeInstance() {
        return new TimeSource("System.nanoTime", () -> System.nanoTime() / 1_000_000_000.0);
    }
}
