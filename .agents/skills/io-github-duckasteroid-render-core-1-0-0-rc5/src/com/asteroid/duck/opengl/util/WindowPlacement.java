package com.asteroid.duck.opengl.util;

import static org.lwjgl.glfw.GLFW.glfwSetWindowPos;

/**
 * Describes where a {@link GLWindow} should be placed when it is first created.
 *
 * <p>Obtain instances via the factory methods:</p>
 * <ul>
 *   <li>{@link #defaultPlacement()} — let the OS decide (current behaviour)</li>
 *   <li>{@link #centeredOn(Monitor)} — centred within a specific monitor</li>
 *   <li>{@link #at(Monitor, int, int)} — at a pixel offset relative to a monitor's top-left</li>
 * </ul>
 *
 * <p>The placement is applied once by {@link GLWindow} immediately after the GLFW window is
 * created, before the window is made visible.</p>
 */
public final class WindowPlacement {

    /** Sentinel: let the operating system choose the window position. */
    private static final WindowPlacement DEFAULT = new WindowPlacement(null, 0, 0, false);

    private final Monitor monitor;
    private final int offsetX;
    private final int offsetY;
    private final boolean center;

    private WindowPlacement(Monitor monitor, int offsetX, int offsetY, boolean center) {
        this.monitor = monitor;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.center = center;
    }

    /** Let the OS decide the initial window position. */
    public static WindowPlacement defaultPlacement() {
        return DEFAULT;
    }

    /**
     * Centre the window on the given monitor.
     *
     * @param monitor the target monitor
     */
    public static WindowPlacement centeredOn(Monitor monitor) {
        return new WindowPlacement(monitor, 0, 0, true);
    }

    /**
     * Place the window at pixel offset {@code (x, y)} relative to the top-left corner of the
     * given monitor's virtual-desktop position.
     *
     * @param monitor the target monitor
     * @param x       horizontal offset in screen coordinates from the monitor's left edge
     * @param y       vertical offset in screen coordinates from the monitor's top edge
     */
    public static WindowPlacement at(Monitor monitor, int x, int y) {
        return new WindowPlacement(monitor, x, y, false);
    }

    /**
     * Applies this placement to an existing GLFW window. Called by {@link GLWindow} after
     * {@code glfwCreateWindow} and before {@code glfwShowWindow}.
     *
     * @param windowHandle GLFW window handle
     * @param windowWidth  window width in screen coordinates
     * @param windowHeight window height in screen coordinates
     */
    void apply(long windowHandle, int windowWidth, int windowHeight) {
        if (monitor == null) return; // default: OS decides
        int px, py;
        if (center) {
            px = monitor.x() + (monitor.width() - windowWidth) / 2;
            py = monitor.y() + (monitor.height() - windowHeight) / 2;
        } else {
            px = monitor.x() + offsetX;
            py = monitor.y() + offsetY;
        }
        glfwSetWindowPos(windowHandle, px, py);
    }
}
