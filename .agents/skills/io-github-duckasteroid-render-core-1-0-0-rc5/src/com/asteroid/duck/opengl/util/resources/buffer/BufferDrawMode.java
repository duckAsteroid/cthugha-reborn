package com.asteroid.duck.opengl.util.resources.buffer;

import com.asteroid.duck.opengl.util.GLCoded;

import static org.lwjgl.opengl.GL11.*;

/**
 * OpenGL primitive topology used by draw calls such as {@code glDrawArrays} and {@code glDrawElements}.
 */
public enum BufferDrawMode implements GLCoded {
    /**
     * Interprets each vertex as a single point.
     */
    POINTS(GL_POINTS),
    /**
     * Interprets each pair of vertices as a line segment.
     */
    LINES(GL_LINES),
    /**
     * Each vertex extends the line from the previous one; one contiguous polyline for the entire buffer.
     * Useful for waveform visualisers where all vertices are part of a single stroke.
     */
    LINE_STRIP(GL_LINE_STRIP),
    /**
     * Like {@link #LINE_STRIP} but the last vertex is implicitly connected back to the first,
     * closing the polyline. Useful for closed shapes such as the {@link com.asteroid.duck.opengl.util.wave.RadialWave}.
     */
    LINE_LOOP(GL_LINE_LOOP),
    /**
     * Every three vertices form an independent triangle. The most general triangle primitive;
     * use when triangles do not share edges.
     */
    TRIANGLES(GL_TRIANGLES),
    /**
     * The first three vertices form a triangle; each subsequent vertex extends the strip by one
     * triangle sharing an edge with the previous. Efficient for quads and terrain meshes.
     */
    TRIANGLE_STRIP(GL_TRIANGLE_STRIP),
    /**
     * The first vertex is a fixed "hub"; each subsequent pair of vertices forms a triangle with
     * the hub. Efficient for circle approximations and convex polygon fills.
     */
    TRIANGLE_FAN(GL_TRIANGLE_FAN);

    final int glCode;

    BufferDrawMode(int glCode) {
        this.glCode = glCode;
    }

    /**
     * Returns the underlying OpenGL enum value.
     *
     * @return GL primitive constant (for example {@code GL_TRIANGLES})
     */
    public int openGlCode() {
        return glCode;
    }
}
