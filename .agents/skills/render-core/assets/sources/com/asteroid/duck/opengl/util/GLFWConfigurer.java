package com.asteroid.duck.opengl.util;

/**
 * Optional hooks a {@link GLWindow} runs while it sets up GLFW, for configuration that has to
 * happen before the library or the window exists.
 *
 * <p>Both hooks run on the thread constructing the window, after {@link GLWindow}'s own
 * defaults have been applied, so anything set here overrides them:</p>
 * <ul>
 *   <li>{@link #beforeInit()} — just before {@code glfwInit()}; set init hints here, e.g.
 *       {@code glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_WAYLAND)} (GLWindow defaults to X11
 *       on Linux).</li>
 *   <li>{@link #beforeCreateWindow()} — after {@code glfwInit()}, just before
 *       {@code glfwCreateWindow()}; set window hints here, e.g.
 *       {@code glfwWindowHint(GLFW_SAMPLES, 4)} or {@code glfwWindowHint(GLFW_CONTEXT_DEBUG, GLFW_FALSE)}.</li>
 * </ul>
 *
 * <p>No GL context is current during either hook, so don't make GL calls from them.</p>
 *
 * <pre>{@code
 * // One hook, as a lambda:
 * GLFWConfigurer.beforeInit(() -> glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_WAYLAND));
 *
 * // Both hooks:
 * new GLFWConfigurer() {
 *     public void beforeInit()         { glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_WAYLAND); }
 *     public void beforeCreateWindow() { glfwWindowHint(GLFW_SAMPLES, 4); }
 * };
 * }</pre>
 */
public interface GLFWConfigurer {

    /** Configurer that changes nothing; GLWindow's defaults apply. */
    GLFWConfigurer NONE = new GLFWConfigurer() {};

    /** Called just before {@code glfwInit()}; set GLFW init hints here. */
    default void beforeInit() {}

    /** Called just before {@code glfwCreateWindow()}; set GLFW window hints here. */
    default void beforeCreateWindow() {}

    /**
     * A configurer that only runs {@code action} before {@code glfwInit()}.
     *
     * @param action sets init hints, e.g. {@code glfwInitHint(GLFW_PLATFORM, ...)}
     * @return a configurer running {@code action} as its {@link #beforeInit()} hook
     */
    static GLFWConfigurer beforeInit(Runnable action) {
        return new GLFWConfigurer() {
            @Override
            public void beforeInit() {
                action.run();
            }
        };
    }

    /**
     * A configurer that only runs {@code action} before {@code glfwCreateWindow()}.
     *
     * @param action sets window hints, e.g. {@code glfwWindowHint(GLFW_SAMPLES, 4)}
     * @return a configurer running {@code action} as its {@link #beforeCreateWindow()} hook
     */
    static GLFWConfigurer beforeCreateWindow(Runnable action) {
        return new GLFWConfigurer() {
            @Override
            public void beforeCreateWindow() {
                action.run();
            }
        };
    }
}
