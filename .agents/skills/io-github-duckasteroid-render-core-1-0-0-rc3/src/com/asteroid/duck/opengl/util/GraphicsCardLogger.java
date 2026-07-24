package com.asteroid.duck.opengl.util;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.GL_RENDERER;
import static org.lwjgl.opengl.GL11.glGetString;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Minimal GLFW/OpenGL bootstrap that prints the GPU renderer string and exits.
 *
 * <p>Run via {@code ./gradlew :experiments:graphicsCardLogger} to confirm which physical GPU
 * LWJGL is using on the current system — useful for diagnosing Optimus / prime-offload issues
 * where the discrete GPU may not be selected by default.</p>
 */
public class GraphicsCardLogger {

    private static final Logger LOG = LoggerFactory.getLogger(GraphicsCardLogger.class);

    /** Default constructor; this class is only ever used via {@link #main}. */
    public GraphicsCardLogger() {}

    /**
     * Initialise GLFW, create a minimal window to obtain an OpenGL context, print the
     * {@code GL_RENDERER} string (which identifies the active GPU), then tear everything down.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(String[] args) {
        GLFWErrorCallback.create((error, description) ->
                LOG.error("GLFW error [{}]: {}", error, GLFWErrorCallback.getDescription(description))).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        long window = glfwCreateWindow(600, 400, "LWJGL GPU Info", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        glfwMakeContextCurrent(window);
        GL.createCapabilities();

        String gpuName = glGetString(GL_RENDERER);
        LOG.info("GPU Renderer: {}", gpuName);

        glfwDestroyWindow(window);
        glfwTerminate();
    }
}
