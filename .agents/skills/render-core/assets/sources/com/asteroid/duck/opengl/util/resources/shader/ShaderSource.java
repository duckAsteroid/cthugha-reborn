package com.asteroid.duck.opengl.util.resources.shader;

/**
 * Used to give a shader source and to indicate the location of the resource
 * @param source the actual source code in GLSL
 * @param location a hint about the location the source came from
 */
public record ShaderSource(String source, String location) {
    /**
     * Create a {@link ShaderSource} whose location hint records that the GLSL source was defined
     * as a string literal in the given class, rather than loaded from a file.
     *
     * @param source   the full GLSL source code
     * @param location the class in which the source string is declared (used for error messages)
     * @return a new {@link ShaderSource} with the source and a descriptive location string
     */
    public static ShaderSource fromClass(String source, Class<?> location) {
        return new ShaderSource(source, "String variable in " + location.getName());
    }

    /**
     * Returns {@code true} if the GLSL source is absent or contains only whitespace.
     * Used by {@link com.asteroid.duck.opengl.util.resources.shader.ShaderProgram#compile} to
     * determine whether an optional geometry shader should be compiled.
     *
     * @return {@code true} if source is {@code null} or blank
     */
    public boolean isSourceBlank() {
        return source == null || source.isBlank();
    }
}
