package com.asteroid.duck.opengl.util;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Describes a connected display monitor: its name, virtual-desktop position, video-mode
 * dimensions, and refresh rate. The underlying GLFW monitor handle is retained so instances
 * can be passed directly to GLFW calls or {@link WindowPlacement}.
 *
 * <p>Instances are snapshots — they reflect the monitor layout at the time of the call.
 * Re-enumerate with {@link #getAll()} if the display configuration may have changed.</p>
 */
public record Monitor(long handle, String name, int x, int y, int width, int height, int refreshRate) {

    /**
     * Returns all currently connected monitors. The primary monitor is always first.
     * Returns an empty list if GLFW has not been initialised.
     */
    public static List<Monitor> getAll() {
        var buf = glfwGetMonitors();
        if (buf == null) return Collections.emptyList();
        List<Monitor> result = new ArrayList<>(buf.limit());
        long primary = glfwGetPrimaryMonitor();
        while (buf.hasRemaining()) {
            Monitor m = fromHandle(buf.get());
            if (m.handle == primary) {
                result.addFirst(m);
            } else {
                result.add(m);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Returns the primary monitor. */
    public static Monitor primary() {
        return fromHandle(glfwGetPrimaryMonitor());
    }

    /** Wraps a raw GLFW monitor handle. */
    static Monitor fromHandle(long handle) {
        String name = glfwGetMonitorName(handle);
        int[] mx = {0}, my = {0};
        glfwGetMonitorPos(handle, mx, my);
        GLFWVidMode mode = glfwGetVideoMode(handle);
        int w = mode != null ? mode.width() : 0;
        int h = mode != null ? mode.height() : 0;
        int hz = mode != null ? mode.refreshRate() : 0;
        return new Monitor(handle, name != null ? name : "", mx[0], my[0], w, h, hz);
    }

    /** The monitor's bounding rectangle in virtual desktop coordinates. */
    public Rectangle bounds() {
        return new Rectangle(x, y, width, height);
    }

    /** The centre of this monitor in virtual desktop coordinates. */
    public int centerX() { return x + width / 2; }
    public int centerY() { return y + height / 2; }

    @Override
    public String toString() {
        return name + " (" + width + "x" + height + "@" + refreshRate + "Hz at " + x + "," + y + ")";
    }
}
