package com.asteroid.duck.opengl.util;

import com.asteroid.duck.opengl.util.events.ResizeListener;
import com.asteroid.duck.opengl.util.keys.KeyRegistry;
import com.asteroid.duck.opengl.util.resources.manager.ResourceManager;
import com.asteroid.duck.opengl.util.timer.Clock;
import com.asteroid.duck.opengl.util.resources.texture.Texture;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.awt.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Random;

/**
 * The interface that OpenGL rendered components can use to interact with the context (typically the
 * window) they are rendered in.
 */
public interface RenderContext {
	/**
	 * A key registry to set up key handlers
	 * @return the key registry
	 */
	KeyRegistry getKeyRegistry();

	/**
	 * The current position and size of the GLFW window in screen coordinates.
	 * On high-DPI displays these values may differ from the GL framebuffer dimensions.
	 * For pixel-accurate projection use {@link #ortho()} rather than these dimensions.
	 * @return window position and size in screen coordinates
	 */
	Rectangle getWindow();

	/**
	 * Returns the {@link Monitor} that currently contains the largest area of this window.
	 * Falls back to the primary monitor if no overlap is found.
	 */
	default Monitor getCurrentMonitor() {
		return Monitor.primary();
	}

	/**
	 * Register a listener that is notified whenever the window's framebuffer is resized.
	 * Safe to call at any time, including from within a render callback.
	 *
	 * @param listener the resize callback to add; no-op if already registered
	 */
	void addResizeListener(ResizeListener listener);

	/**
	 * Deregister a previously added resize listener.
	 *
	 * @param listener the callback to remove; no-op if it was never registered
	 */
	void removeResizeListener(ResizeListener listener);

	/**
	 * The timer in operation - used for elapsed time etc.
	 * @return the timer instance
	 */
	Clock getClock();

	/**
     * A resource manager for handling resources like
     * {@link Texture}s, {@link com.asteroid.duck.opengl.util.resources.shader.ShaderProgram}s etc
     *
     * @return the resource manager instance
     */
	ResourceManager getResourceManager();

	/**
	 * Should the screen be cleared on each render loop cycle
	 * @param clear true to clear
	 */
	void setClearScreen(boolean clear);

	/**
	 * Get the current state of screen clearing in the render loop
	 * @return true if screen cleared during render cycle
	 */
	boolean isClearScreen();

	/**
	 * The background colour the screen is cleared to in the render loop
	 * (if {@link #isClearScreen()} is true)
	 * @return the current background colour
	 */
	Vector4f getBackgroundColor();
	/**
	 * Set the RGBA colour the screen is cleared to each frame when {@link #isClearScreen()} is {@code true}.
	 *
	 * @param vector4f the new background colour as an RGBA vector in [0, 1] per component
	 */
	void setBackgroundColor(Vector4f vector4f);

	/**
	 * The target update period (if defined) in seconds.
	 * <code>null</code> means the programme runs as fast as possible
	 * @return current desired update period in seconds
	 */
	Double getDesiredUpdatePeriod();
	/**
	 * Set the target frame period directly in seconds.
	 * A value of {@code null} removes the cap and lets the loop run as fast as possible.
	 *
	 * @param period the desired frame period in seconds, or {@code null} for uncapped
	 */
	void setDesiredUpdatePeriod(Double period);

	/**
	 * A frequency (hz) based version of the update period
	 * @return the update period (if any) in hertz
	 */
	default Double getDesiredUpdateFrequency() {
		return getDesiredUpdatePeriod() == null ? null : 1.0 / getDesiredUpdatePeriod();
	}

	/**
	 * Set the target frame rate in hertz. Converts to a period and delegates to
	 * {@link #setDesiredUpdatePeriod}. Pass {@code null} to remove the cap.
	 *
	 * @param frequencyInHertz the desired update rate in Hz, or {@code null} for uncapped
	 */
	default void setDesiredUpdateFrequency(Double frequencyInHertz) {
		if (frequencyInHertz != null) {
			setDesiredUpdatePeriod(1.0 / frequencyInHertz);
		}
		else {
			setDesiredUpdatePeriod(null);
		}
	}

	/**
	 * The orthographic projection matrix sized to the current GL viewport (framebuffer pixels).
	 * Origin is at top-left (0,0); axes extend right and down to viewport width/height.
	 * On high-DPI displays the framebuffer may be larger than the GLFW window's screen dimensions.
	 * @return a matrix suitable for use as an orthographic projection
	 */
	Matrix4f ortho();

	/**
	 * A random number generator instance for use by rendered components
	 * @return the random instance
	 */
    Random getRandom();

	/**
	 * Request capture of the next rendered frame as a PNG file.
	 *
	 * <p>The capture is deferred: it occurs after the next {@link RenderedItem#doRender} pass
	 * completes but before the framebuffer is swapped to screen, so the image reflects exactly
	 * what will be displayed. The PNG is written on a background thread to avoid stalling the
	 * render loop.</p>
	 *
	 * <p>Safe to call from any thread (including key callbacks). Only the most recently
	 * requested path is captured if multiple calls arrive before a frame completes.</p>
	 *
	 * @param destination path for the PNG output file
	 */
	default void captureNextFrame(Path destination) { }

	/**
	 * Convenience overload: captures the next frame to a timestamped PNG in the working directory.
	 *
	 * @see #captureNextFrame(Path)
	 */
	default void captureNextFrame() {
		captureNextFrame(Path.of("screenshot-" + System.currentTimeMillis() + ".png"));
	}

	/**
	 * Start recording the render output as an H.264/MP4 video for the specified duration.
	 *
	 * <p>Encoding is performed on a background thread so the render loop is not stalled.
	 * If a recording is already in progress the new request is ignored. The MP4 file is
	 * finalised automatically when the duration expires; call {@link #stopRecording()} to
	 * end it early.</p>
	 *
	 * <p>Safe to call from any thread (including key callbacks).</p>
	 *
	 * @param destination path for the MP4 output file
	 * @param duration    how long to record; capped at 60 seconds
	 */
	default void startRecording(Path destination, Duration duration) { }

	/**
	 * Convenience overload: records to a timestamped MP4 in the working directory.
	 *
	 * @param duration how long to record; capped at 60 seconds
	 * @see #startRecording(Path, Duration)
	 */
	default void startRecording(Duration duration) {
		startRecording(Path.of("recording-" + System.currentTimeMillis() + ".mp4"), duration);
	}

	/**
	 * Stop an in-progress recording before its duration expires.
	 * No-op if no recording is active.
	 */
	default void stopRecording() { }
}
