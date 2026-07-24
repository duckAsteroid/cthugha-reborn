package com.asteroid.duck.opengl.util.resources.buffer;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.RenderedItem;

import com.asteroid.duck.opengl.util.resources.bound.BindingException;
import com.asteroid.duck.opengl.util.resources.bound.ExclusivityGroup;
import com.asteroid.duck.opengl.util.resources.buffer.ebo.ElementBufferObject;
import com.asteroid.duck.opengl.util.resources.buffer.vbo.VertexBufferObject;
import com.asteroid.duck.opengl.util.resources.buffer.vbo.VertexDataStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.opengl.GL30.*;

/**
 * Represents an OpenGL Vertex Array Object (VAO) that encapsulates vertex buffer state.
 * <p>
 * A VAO is a container that stores the configuration of vertex attributes and their associated
 * buffer objects. This class manages the relationship between:
 * <ul>
 *   <li><b>VAO (Vertex Array Object)</b>: The OpenGL object that records vertex attribute state</li>
 *   <li><b>VBO (Vertex Buffer Object)</b>: Contains vertex position and attribute data</li>
 *   <li><b>EBO (Element Buffer Object)</b>: Optional index buffer for indexed rendering</li>
 * </ul>
 * <p>
 * <b>Lifecycle:</b>
 * <ol>
 *   <li>Create a new VertexArrayObject</li>
 *   <li>Optionally create a VBO via {@link #createVbo(VertexDataStructure, int)} for vertex data</li>
 *   <li>Optionally create an EBO via {@link #createEbo(int)} for indexed rendering</li>
 *   <li>Call {@link #init(RenderContext)} to initialize OpenGL resources</li>
 *   <li>Call {@link #doRender(RenderContext)} during render loop to draw the geometry</li>
 * </ol>
 * <p>
 * <b>Rendering Modes:</b>
 * <ul>
 *   <li>With EBO: Uses {@code glDrawElements()} to render indexed vertices</li>
 *   <li>With VBO only: Uses {@code glDrawArrays()} to render vertex range sequentially</li>
 * </ul>
 * <p>
 * <b>Thread Safety:</b> Not thread-safe. All operations must occur on the render thread.
 * The binding state is managed by an exclusivity group to prevent simultaneous VAO bindings.
 *
 * @see VertexBufferObject
 * @see ElementBufferObject
 * @see BufferDrawMode
 */
public class VertexArrayObject  implements RenderedItem {
    private static final Logger log = LoggerFactory.getLogger(VertexArrayObject.class);

    /**
     * The OpenGL handle to this Vertex Array Object.
     * Null until {@link #init(RenderContext)} is called.
     */
    private Integer vao = null;

    /**
     * Optional Vertex Buffer Object containing vertex position and attribute data.
     * Created via {@link #createVbo(VertexDataStructure, int)}.
     * If null, rendering will use {@code glDrawArrays()}.
     */
    private VertexBufferObject vbo;

    /**
     * Optional Element Buffer Object containing vertex indices for indexed rendering.
     * Created via {@link #createEbo(int)}.
     * If present, rendering will use {@code glDrawElements()}.
     */
    private ElementBufferObject ebo;

    /**
     * The OpenGL primitive draw mode (triangles, lines, points, etc.).
     * Default is {@link BufferDrawMode#TRIANGLES}.
     * Can be changed via {@link #setDrawMode(BufferDrawMode)}.
     *
     * @see BufferDrawMode
     */
    private BufferDrawMode drawMode = BufferDrawMode.TRIANGLES;

    /**
     * Manages exclusive access to this VAO binding to prevent conflicts
     * when multiple VAOs exist in the application.
     */
    private ExclusivityGroup<VertexArrayObject> binding;

    /**
     * Checks if this VAO has an associated Vertex Buffer Object.
     *
     * @return true if a VBO has been created and exists, false otherwise
     * @see #createVbo(VertexDataStructure, int)
     */
    public boolean hasVbo() {
        return vbo != null;
    }

    /**
     * Checks if this VAO has an associated Element Buffer Object.
     *
     * @return true if an EBO has been created and exists, false otherwise
     * @see #createEbo(int)
     */
    public boolean hasEbo() {
        return ebo != null;
    }

    /**
     * Retrieves the Vertex Buffer Object associated with this VAO.
     *
     * @return the VBO, or null if none has been created
     * @see #createVbo(VertexDataStructure, int)
     * @see #hasVbo()
     */
    public VertexBufferObject getVbo() {
        return vbo;
    }

    /**
     * Retrieves the OpenGL handle (name) for this Vertex Array Object.
     * <p>
     * This is the OpenGL integer identifier used in low-level OpenGL calls.
     * The handle is only valid after {@link #init(RenderContext)} has been called.
     *
     * @return the OpenGL VAO handle
     * @throws BindingException if this VAO has not been initialized yet
     */
    public int id() throws BindingException {
        if (vao == null) throw new BindingException("Not initialised");
        return vao;
    }

    /**
     * Creates and associates a Vertex Buffer Object (VBO) with this VAO.
     * <p>
     * A VBO stores vertex data (positions, colors, normals, etc.) in GPU memory.
     * The VBO describes the structure of vertex data using a {@link VertexDataStructure}
     * and allocates GPU memory with the specified capacity.
     * <p>
     * This method must be called before {@link #init(RenderContext)} to ensure proper
     * initialization of the VAO-VBO relationship. Only one VBO can be created per VAO.
     *
     * @param structure describes the layout of vertex attributes (e.g., position, color)
     * @param capacity the maximum number of vertices this VBO can store
     * @return the created VertexBufferObject
     * @throws IllegalStateException if a VBO has already been created for this VAO
     * @see VertexBufferObject
     * @see VertexDataStructure
     */
    public VertexBufferObject createVbo(VertexDataStructure structure, int capacity) {
        if (this.vbo != null) {
            throw new IllegalStateException("VBO already exists");
        }
        this.vbo = new VertexBufferObject(this, structure, capacity);
        return vbo;
    }

    /**
     * Retrieves the Element Buffer Object associated with this VAO.
     *
     * @return the EBO, or null if none has been created
     * @see #createEbo(int)
     * @see #hasEbo()
     */
    public ElementBufferObject getEbo() {
        return ebo;
    }

    /**
     * Creates and associates an Element Buffer Object (EBO) with this VAO.
     * <p>
     * An EBO stores indices that reference vertices in the VBO, enabling efficient
     * indexed rendering via {@code glDrawElements()}. This allows rendering the same
     * vertex multiple times without duplicating its data.
     * <p>
     * This method must be called before {@link #init(RenderContext)} to ensure proper
     * initialization. Only one EBO can be created per VAO.
     *
     * @param capacity the maximum number of indices this EBO can store
     * @return the created ElementBufferObject
     * @throws IllegalStateException if an EBO has already been created for this VAO
     * @see ElementBufferObject
     */
    public ElementBufferObject createEbo(int capacity) {
        if (this.ebo != null) {
            throw  new IllegalStateException("EBO already exists");
        }
        this.ebo = new ElementBufferObject(this, capacity);
        return ebo;
    }

    /**
     * Gets the current primitive draw mode for this VAO.
     *
     * @return the current draw mode (default is {@link BufferDrawMode#TRIANGLES})
     * @see BufferDrawMode
     */
    public BufferDrawMode getDrawMode() {
        return drawMode;
    }

    /**
     * Sets the primitive draw mode for this VAO.
     * <p>
     * The draw mode determines how vertices are interpreted as primitives during rendering
     * (e.g., triangles, lines, points, triangle strips, etc.).
     * <p>
     * This can be changed at any time, even after {@link #init(RenderContext)}.
     *
     * @param drawMode the new draw mode to use
     * @see BufferDrawMode
     */
    public void setDrawMode(BufferDrawMode drawMode) {
        this.drawMode = drawMode;
    }

    /**
     * Initializes this VAO and its associated VBO/EBO on the GPU.
     * <p>
     * This method must be called on the render thread before the VAO can be used.
     * It performs the following:
     * <ol>
     *   <li>Creates the OpenGL VAO object (via {@code glGenVertexArrays()})</li>
     *   <li>Sets up the exclusivity group for binding state management</li>
     *   <li>Initializes the VBO if one was created (via {@link VertexBufferObject#init(RenderContext)})</li>
     *   <li>Initializes the EBO if one was created (via {@link ElementBufferObject#init(RenderContext)})</li>
     * </ol>
     *
     * @param ctx the render context providing access to OpenGL and resource management
     * @see #bind(RenderContext)
     * @see #doRender(RenderContext)
     */
    public void init(RenderContext ctx) {
        this.binding = ctx.getResourceManager().exclusivityGroup(VertexArrayObject.class);
        vao = glGenVertexArrays();
        bind(ctx);
        if (vbo != null) {
            vbo.init(ctx);
        }
        if (ebo != null) {
            ebo.init(ctx);
        }
    }

    /**
     * Binds this VAO to the current OpenGL context.
     * <p>
     * Binding makes this VAO the active vertex array object, so subsequent operations
     * on vertex attributes and buffers affect this VAO. Only one VAO can be bound at a time.
     * <p>
     * The exclusivity group ensures that binding this VAO automatically unbinds any other
     * previously bound VAO to maintain a valid GL state.
     *
     * @param ctx the render context
     */
    public void bind(RenderContext ctx) {
        binding.bind(this);
    }

    /**
     * Unbinds this VAO from the current OpenGL context.
     * <p>
     * After unbinding, subsequent vertex attribute operations will not affect this VAO.
     *
     * @param ctx the render context
     * @throws BindingException if this VAO is not currently bound
     */
    protected void unbind(RenderContext ctx) throws BindingException {
        binding.unbind(this);
    }

    /**
     * Renders the geometry stored in this VAO.
     * <p>
     * This method is called during the render loop. It:
     * <ol>
     *   <li>Binds this VAO to the GL context</li>
     *   <li>If an EBO exists: calls {@code glDrawElements()} to render indexed geometry</li>
     *   <li>Otherwise if a VBO exists: calls {@code glDrawArrays()} to render vertices sequentially</li>
     *   <li>Uses the current {@link #drawMode} to determine the primitive type</li>
     * </ol>
     *
     * @param ctx the render context
     * @see #setDrawMode(BufferDrawMode)
     * @see VertexBufferObject
     * @see ElementBufferObject
     */
    @Override
    public void doRender(RenderContext ctx) {
        bind(ctx);
        // if we have an EBO (i.e. a set of indices) use glDrawElements
        if (ebo != null) {
            glDrawElements(drawMode.glCode, ebo.capacity(), ebo.getType(), 0);
        }
        // otherwise if we have a VBO glDrawArrays
        else if (vbo != null) {
            glDrawArrays(drawMode.glCode, 0, vbo.size());
        }
        else {
            log.warn("Neither EBO or VBO initialised");
        }
    }

    /**
     * Releases all GPU resources associated with this VAO.
     * <p>
     * This includes:
     * <ul>
     *   <li>The VAO itself (via {@code glDeleteVertexArrays()})</li>
     *   <li>The VBO if present (via {@link VertexBufferObject#dispose()})</li>
     *   <li>The EBO if present (via {@link ElementBufferObject#dispose()})</li>
     * </ul>
     * <p>
     * After calling dispose(), this VAO cannot be used and should be discarded.
     * Should be called during application shutdown or cleanup.
     */
    @Override
    public void dispose() {
        if (vbo != null) {
            vbo.dispose();
            vbo = null;
        }
        if (ebo != null) {
            ebo.dispose();
            ebo = null;
        }
        if (vao != null) {
            glDeleteVertexArrays(vao);
        }
    }

    /**
     * Returns a string representation of this VAO.
     *
     * @return a string containing the OpenGL handle of this VAO
     */
    @Override
    public String toString() {
        return "VAO(id=" + vao + ")";
    }
}
