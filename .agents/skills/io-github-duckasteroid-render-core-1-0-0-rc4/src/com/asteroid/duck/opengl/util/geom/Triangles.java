package com.asteroid.duck.opengl.util.geom;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.RenderedItem;
import com.asteroid.duck.opengl.util.color.StandardColors;
import com.asteroid.duck.opengl.util.resources.buffer.UpdateHint;
import com.asteroid.duck.opengl.util.resources.buffer.VertexArrayObject;
import com.asteroid.duck.opengl.util.resources.buffer.ebo.ElementBufferObject;
import com.asteroid.duck.opengl.util.resources.buffer.vbo.*;
import com.asteroid.duck.opengl.util.resources.shader.ShaderProgram;
import com.asteroid.duck.opengl.util.timer.function.WaveFunction;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.IOException;

/**
 * Represents a set of triangular vertices and the buffers that can be used to render them.
 */
public class Triangles implements RenderedItem {

    private final Vector2f[] vertices;
    private final short[] indices;
    private final int triangleCount;

    private final VertexArrayObject vao = new VertexArrayObject();
    private VertexBufferObject vbo;
    private ElementBufferObject ebo;
    private ShaderProgram shaderProgram;

    private Vector4f color = StandardColors.RED.color;
    private Vector3f freq = null;

    public static Vector2f[] from(float[] raw) {
        Vector2f[] result = new Vector2f[raw.length / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = new Vector2f(raw[i * 2], raw[(i * 2) + 1]);
        }
        return result;
    }

    public static short[] shorten(int[] indices) {
        short[] result = new short[indices.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = (short) indices[i];
        }
        return result;
    }

	public Triangles(float[] vertices, int[] indices) {
        this(from(vertices), shorten(indices));
    }

    public Triangles(Vector2f[] vertices, short[] indices) {
        this.triangleCount = indices.length / 3;
        this.vertices = vertices;
        this.indices = indices;
	}

    public ShaderProgram getShaderProgram() {
        return shaderProgram;
    }

    public void setShaderProgram(ShaderProgram shaderProgram) {
        this.shaderProgram = shaderProgram;
    }

    public Vector4f getColor() {
        return color;
    }

    public void setColor(Vector4f color) {
        this.color = color;
    }

    public Vector3f getFreq() {
        return freq;
    }

    public void setFreq(Vector3f freq) {
        this.freq = freq;
    }

    @Override
    public void init(RenderContext ctx) throws IOException {
        initBuffers(ctx);
        initShader(ctx);
    }

    private void initBuffers(RenderContext ctx) {
        vao.init(ctx);
        // Create a VBO and bind it
        var structure = new VertexDataStructure(new VertexElement(VertexElementType.VEC_2F, "position"));
        vbo = vao.createVbo(structure, vertices.length);
        vbo.init(ctx);
        for(int v = 0; v < vertices.length; v++) {
            vbo.set(v, vertices[v]);
        }
        vbo.update(UpdateHint.STATIC);
        // Create an EBO and bind it
        ebo = vao.createEbo(indices.length);
        ebo.init(ctx);
        ebo.put(indices);
    }

    private void initShader(RenderContext ctx) throws IOException {
        if (shaderProgram == null) {
            shaderProgram = ctx.getResourceManager().getShader("simple", "simple/vertex.glsl", "simple/frag.glsl", null);
        }
        // setup with VBO
        shaderProgram.use(ctx);
		// setup the vertex attribute pointer to tell GL what shape our vertices are (2 floats)
		vbo.setup(shaderProgram);
	}

	public int triangleCount() {
		return triangleCount;
	}

	public int vertices() {
		return triangleCount * 3;
	}

    @Override
    public void doRender(RenderContext ctx) {
        shaderProgram.use(ctx);
        if (shaderProgram.uniforms().has("color")) {
            Vector4f tempColor = color;
            if (freq != null) {
                double elapsed = ctx.getClock().elapsed();
                tempColor = new Vector4f();
                tempColor.x = (float) WaveFunction.value(freq.x, color.x, elapsed);
                tempColor.y = (float) WaveFunction.value(freq.y, color.y, elapsed);
                tempColor.z = (float) WaveFunction.value(freq.z, color.z, elapsed);
            }
            shaderProgram.uniforms().get("color", Vector4f.class).set(tempColor);
        }
        vao.doRender(ctx);
    }

	public void dispose() {
		vbo.dispose();
		ebo.dispose();
	}


    public static Triangles fullscreen() {
        // Define the vertices of the rectangle
        // TL     1      TR
        //
        // -1     0      1
        //
        // BL    -1      BR
        float[] vertices = {
                -1.0f, -1.0f, // bottom left [0]
                1.0f, -1.0f, // bottom right [1]
                1.0f, 1.0f, // top right [2]
                -1.0f, 1.0f // top left [3]
        };

        // TL     1      TR
        //            /  |
        // -1     0      1
        //    /          |
        // BL  -  -1  -  BR
        // BL, BR, TR

        // TL  -  1  -   TR
        // |          /
        // -1     0      1
        // |   /
        // BL     -1     BR
        // BL, TR, TL
        int[] indices = new int[]{
                0, 1, 2, // triangle 1
                0, 2, 3}; // triangle 2
        return new Triangles(vertices, indices);
    }

    public static Triangles singleTriangle() {
        // Define the vertices of the rectangle
        // Y
        // ^
        // TL     1      TR
        //
        // -1     0      1
        //
        // BL    -1      BR   -> X
        float[] vertices = {
                -1f, -1f, // bottom left [0]
                1f, -1f, // bottom right [1]
                0.0f, 1f // top middle [2]
        };


        int[] indices = new int[]{
                0, 1, 2}; // triangle 1
        return new Triangles(vertices, indices);
    }

    public static Triangles centralTriangle() {
        // Define the vertices of the rectangle
        // Y
        // ^
        // TL     1      TR
        //
        // -1     0      1
        //
        // BL    -1      BR   -> X
        float[] vertices = {
                -.5f, -.5f, // bottom left [0]
                .5f, -.5f, // bottom right [1]
                0.0f, .5f // top middle [2]
        };


        int[] indices = new int[]{
                0, 2, 1}; // triangle 1
        return new Triangles(vertices, indices);
    }

}
