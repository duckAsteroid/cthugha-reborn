package com.asteroid.duck.opengl.util.geom;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.resources.buffer.UpdateHint;
import com.asteroid.duck.opengl.util.resources.buffer.VertexArrayObject;
import com.asteroid.duck.opengl.util.resources.buffer.debug.VertexBufferVisualiser;
import com.asteroid.duck.opengl.util.resources.buffer.ebo.ElementBufferObject;
import com.asteroid.duck.opengl.util.resources.buffer.vbo.*;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * A vertex data buffer that contains a rectangle made from two triangles.
 */
public class Rectangle {
    private static final Logger LOG = LoggerFactory.getLogger(Rectangle.class);
    /**
     * Screen normal coordinates are from -1,-1 (bottom left) to +1, +1 (top right)
     */
    public static Vector4f SCREEN_NORMAL = new Vector4f(-1,-1,1,1);
    /**
     * Texture normal coordinates are from 0,0 (bottom left) to 1, 1 (top right)
     */
    public static Vector4f TEXTURE_NORMAL = new Vector4f(0,0,1,1);

    private final VertexArrayObject vao = new VertexArrayObject();
    private final ElementBufferObject ebo;
    private final VertexBufferObject vbo;

    /**
     * Allocate and upload the VAO, VBO, and EBO for a screen-aligned rectangle.
     *
     * <p>The four corners are computed by mapping {@link #SCREEN_NORMAL} and {@link #TEXTURE_NORMAL}
     * through the standard {@link Vertice} layout. The EBO indexes the corners into two clockwise
     * triangles. All data is uploaded with {@link UpdateHint#STATIC}.</p>
     *
     * @param ctx                   the render context used to initialise GL objects
     * @param scrnPosVertexAttrName GLSL {@code in} variable name for the 2-D screen position attribute;
     *                              must match the vertex shader exactly and be non-blank
     * @param texPosVertexAttrName  GLSL {@code in} variable name for the 2-D texture coordinate attribute;
     *                              must match the vertex shader exactly and be non-blank
     */
    public Rectangle(RenderContext ctx, String scrnPosVertexAttrName, String texPosVertexAttrName) {
        require(scrnPosVertexAttrName, "Screen position vertex attribute name");
        var scrnPos = new VertexElement(VertexElementType.VEC_2F, scrnPosVertexAttrName);

        require(texPosVertexAttrName, "Texture position vertex attribute name");
        var texPos = new VertexElement(VertexElementType.VEC_2F, texPosVertexAttrName);
        // setup our VBO with screen and texture positions for 4 corners of a square
        List<VertexElement> elements = List.of(scrnPos, texPos);
        VertexDataStructure structure = new VertexDataStructure(elements);
        this.vbo = vao.createVbo(structure, 4);
        this.vao.init(ctx);
        // put the four corners into the VBO
        final List<Vertice> fourCorners = Vertice.standardFourVertices().toList();
        for(int i = 0; i < fourCorners.size(); i++) {
            Vertice corner = fourCorners.get(i);
            Vector2f screenPosition = corner.from(SCREEN_NORMAL);
            Vector2f texPosition = corner.from(TEXTURE_NORMAL);
            vbo.set(i, screenPosition, texPosition);
        }
        // commit the data to the VBO
        vbo.update(UpdateHint.STATIC);

        // create an element buffer to "index" the four corners
        this.ebo =vao.createEbo(6);
        ebo.init(ctx);
        List<Short> indices = Vertice.standardSixVertices().map(v -> (short) fourCorners.indexOf(v)).toList();
        ebo.update(indices);

        if (LOG.isTraceEnabled()) {
            LOG.trace("Rectangle VAO: {}", new VertexBufferVisualiser(vao));
        }
    }

    private static void require(String value, String message) {
        Objects.requireNonNull(value, message);
        if(value.isBlank()) {
            throw new IllegalArgumentException(message + " cannot be emtpy");
        }
    }

    /**
     * Return the VAO that owns the VBO and EBO for this rectangle.
     *
     * @return the vertex array object; valid after construction, until {@link #destroy()} is called
     */
    public VertexArrayObject getVertexArrayObject() {
        return vao;
    }

    /**
     * Return the element buffer object that indexes the four corners into two triangles.
     *
     * @return the EBO; valid after construction, until {@link #destroy()} is called
     */
    public ElementBufferObject getElementBufferObject() {
        return ebo;
    }

    /**
     * Return the vertex buffer object that holds the per-corner screen and texture positions.
     *
     * @return the VBO; valid after construction, until {@link #destroy()} is called
     */
    public VertexBufferObject getVertexBufferObject() {
        return vbo;
    }

    /**
     * Release all GL resources (VBO and EBO). The VAO itself is not explicitly freed here;
     * callers that hold a reference to it are responsible for disposing it separately if needed.
     */
    public void destroy() {
        vbo.dispose();
        ebo.dispose();
    }

    /**
     * Draw the rectangle using the currently bound shader program.
     *
     * @param ctx the render context; passed through to the VAO's render method
     */
    public void render(RenderContext ctx) {
        vao.doRender(ctx);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Rectangle:\n");
        sb.append('\t').append(new VertexBufferVisualiser(vao)).append('\n');
        return sb.toString();
    }
}
