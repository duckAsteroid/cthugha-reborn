package com.asteroid.duck.opengl.util.resources.buffer.ebo;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.resources.Resource;
import com.asteroid.duck.opengl.util.resources.bound.BindingException;
import com.asteroid.duck.opengl.util.resources.buffer.BufferDrawMode;
import com.asteroid.duck.opengl.util.resources.buffer.UpdateHint;
import com.asteroid.duck.opengl.util.resources.buffer.VertexArrayObject;
import com.asteroid.duck.opengl.util.resources.buffer.vbo.VertexBufferObject;
import org.lwjgl.BufferUtils;

import java.nio.ShortBuffer;
import java.util.Objects;
import java.util.stream.IntStream;

import static org.lwjgl.opengl.GL15.*;

/**
 * Index buffer (EBO) used for indexed rendering with a {@link VertexBufferObject}.
 *
 * <p>The buffer stores index values referencing vertices in the owner VAO's VBO. This allows
 * vertex reuse and enables draw calls such as {@code glDrawElements}.</p>
 *
 * <p>Typical lifecycle:</p>
 * <ol>
 *   <li>Create with owner VAO and fixed capacity.</li>
 *   <li>Call {@link #init(RenderContext)} in an active OpenGL context.</li>
 *   <li>Fill/modify CPU-side indices with {@link #put(short)}, {@link #put(short[])},
 *       or {@link #put(int, short[])}.</li>
 *   <li>Upload with one of the {@code update(...)} methods.</li>
 *   <li>Dispose with {@link #dispose()} when no longer needed.</li>
 * </ol>
 *
 * <p>Threading: not thread-safe; intended for render/OpenGL thread usage.</p>
 */
public class ElementBufferObject implements Resource {
	private final VertexArrayObject owner;
	/**
	 * The in memory copy of the index buffer
	 */
	private ShortBuffer indexBuffer;
	/**
	 * The GL pointer to the index buffer
	 */
	private Integer ebo = null;
	/**
	 * The maximum number of indices in the buffer
	 */
	private final int capacity;

	private BufferDrawMode drawMode = BufferDrawMode.TRIANGLES;

	/** Legacy constant kept for compatibility; element type actually used is {@code GL_UNSIGNED_SHORT}. */
	public static final int GL_TYPE = GL_UNSIGNED_INT;

	private UpdateHint updateHint = UpdateHint.STATIC;

	/**
	 * Creates a fixed-capacity element/index buffer model.
	 *
	 * @param owner owning VAO
	 * @param capacity number of indices this buffer can store
	 * @throws NullPointerException if {@code owner} is null
	 * @throws IllegalArgumentException if {@code capacity <= 0}
	 */
	public ElementBufferObject(VertexArrayObject owner, int capacity) {
		Objects.requireNonNull(owner, "Vertex array object must not be null");
		this.owner = owner;
		if (capacity <= 0) {
			throw new IllegalArgumentException("Capacity must be greater than zero.");
		}
		this.capacity = capacity;
	}

	/**
	 * Returns the current upload usage hint.
	 *
	 * @return usage hint used for {@code glBufferData}
	 */
	public UpdateHint getUpdateHint() {
		return updateHint;
	}

	/**
	 * Sets default upload hint; null falls back to {@link UpdateHint#STATIC}.
	 *
	 * @param updateHint usage hint for future uploads
	 */
	public void setUpdateHint(UpdateHint updateHint) {
		if (updateHint == null) {
			updateHint = UpdateHint.STATIC;
		}
		this.updateHint = updateHint;
	}

	/**
	 * Returns the GL id of this EBO.
	 *
	 * @return OpenGL buffer id
	 * @throws BindingException if not initialized
	 */
	public int id() throws BindingException {
		if (ebo == null) throw new BindingException("Not initialised");
		return ebo;
	}

	/**
	 * Returns maximum index capacity.
	 *
	 * @return fixed number of index entries this buffer can hold
	 */
	public int capacity() {
		return capacity;
	}

	/**
	 * Allocates CPU-side index buffer and GPU-side EBO and uploads initial data.
	 *
	 * @param ctx active render context
	 */
	public void init(RenderContext ctx) {
		indexBuffer = BufferUtils.createShortBuffer(capacity);
		ebo = glGenBuffers();
		bind(ctx);
		glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexBuffer, updateHint.openGlCode());
	}

	/**
	 * Binds this EBO in the current OpenGL context.
	 *
	 * @param ctx render context
	 */
	protected void bind(RenderContext ctx) {
		var binder = ctx.getResourceManager().exclusivityGroup(ElementBufferObject.class);
		binder.bind(this);
	}

	/**
	 * Unbinds this EBO from the current OpenGL context.
	 *
	 * @param ctx render context
	 * @throws BindingException if this EBO is not currently bound
	 */
	protected void unbind(RenderContext ctx) throws BindingException {
		var binder = ctx.getResourceManager().exclusivityGroup(ElementBufferObject.class);
		binder.unbind(this);
	}

	/**
	 * Replaces buffer contents from the provided array and uploads to GPU.
	 *
	 * @param indices source indices (must fit capacity)
	 */
	public void update(short[] indices) {
		indexBuffer.clear();
		indexBuffer.put(indices);
		indexBuffer.flip();
		glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexBuffer, updateHint.openGlCode());
	}

	/**
	 * Uploads the currently written index buffer contents.
	 *
	 * <p>This flips the internal buffer before upload.</p>
	 */
	public void update() {
		indexBuffer.flip();
		glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexBuffer, updateHint.openGlCode());
	}

	/**
	 * Replaces buffer contents from an iterable and uploads to GPU.
	 *
	 * @param indices source indices (must fit capacity)
	 */
	public void update(Iterable<Short> indices) {
		indexBuffer.clear();
		for (Short index : indices) {
			indexBuffer.put(index);
		}
		indexBuffer.flip();
		glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexBuffer, updateHint.openGlCode());
	}

	/**
	 * Clears the CPU-side write position for subsequent {@code put(...)} calls.
	 */
	public void clear() {
		indexBuffer.clear();
	}

	/**
	 * Writes an index array at the specified write position in the CPU buffer.
	 *
	 * @param index destination start offset
	 * @param indices values to write
	 */
	public void put(int index, short[] indices) {
		indexBuffer.position(index);
		indexBuffer.put(indices);
	}

	/**
	 * Appends index values at the current CPU buffer position.
	 *
	 * @param indices values to append
	 */
	public void put(short[] indices) {
		indexBuffer.put(indices);
	}

	/**
	 * Appends a single index at the current CPU buffer position.
	 *
	 * @param i value to append
	 */
	public void put(short i) {
		indexBuffer.put(i);
	}

	/**
	 * Returns total capacity of the underlying CPU index buffer.
	 *
	 * @return number of storable index entries
	 */
	public int size() {
		return indexBuffer.capacity();
	}

	/**
	 * Reads one index from the CPU-side buffer.
	 *
	 * @param index index position
	 * @return stored index value
	 */
	public short get(int index) {
		return indexBuffer.get(index);
	}

	/**
	 * Returns a stream view over index values in capacity order.
	 *
	 * @return int stream of index values
	 */
	public IntStream intStream() {
		return IntStream.range(0, capacity).map(indexBuffer::get);
	}

	/**
	 * Deletes the GL buffer object and releases CPU-side buffer reference.
	 */
	@Override
	public void dispose() {
		if (ebo != null) {
			glDeleteBuffers(ebo);
		}
		indexBuffer = null;
	}

	/**
	 * Returns a debug-friendly representation of current index contents.
	 *
	 * @return debug string with capacity and current buffer values
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("IndexBuffer{");
		sb.append("capacity=").append(capacity);
		sb.append(", indexBuffer=[");
		if (indexBuffer != null) {
			var tmp = indexBuffer.duplicate();
			while (tmp.hasRemaining()) {
				sb.append(tmp.get());
				if (tmp.hasRemaining()) {
					sb.append(", ");
				}
			}
		}
		else sb.append("null");
		sb.append("]}");
		return sb.toString();
	}

	/**
	 * Returns a stream over a read-only view of index data.
	 *
	 * @return stream of index values, or empty stream when uninitialized
	 */
	public IntStream stream() {
		if (indexBuffer == null) {
			return IntStream.empty();
		}
		final var buffer = indexBuffer.asReadOnlyBuffer().flip();
		return IntStream.generate(() -> {
			if (buffer.hasRemaining()) {
				return buffer.get();
			} else {
				throw new IndexOutOfBoundsException("Index buffer has no more elements.");
			}
		}).limit(buffer.remaining());
	}

	/**
	 * Returns the OpenGL scalar type for index elements in this buffer.
	 *
	 * @return {@code GL_UNSIGNED_SHORT}
	 */
	public int getType() {
		return GL_UNSIGNED_SHORT;
	}


}
