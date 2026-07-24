/**
 * OpenGL buffer abstractions used to move structured vertex/index data from CPU memory to GPU memory.
 *
 * <p>Key types in this package:</p>
 * <ul>
 *   <li>{@link com.asteroid.duck.opengl.util.resources.buffer.VertexArrayObject} for attribute state and ownership.</li>
 *   <li>{@link com.asteroid.duck.opengl.util.resources.buffer.vbo.VertexBufferObject} for per-vertex payload.</li>
 *   <li>{@link com.asteroid.duck.opengl.util.resources.buffer.ebo.ElementBufferObject} for indexed rendering.</li>
 * </ul>
 */
package com.asteroid.duck.opengl.util.resources.buffer;