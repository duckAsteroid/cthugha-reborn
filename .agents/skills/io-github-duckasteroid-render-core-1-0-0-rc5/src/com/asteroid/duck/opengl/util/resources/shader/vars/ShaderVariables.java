package com.asteroid.duck.opengl.util.resources.shader.vars;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.RenderedItem;
import com.asteroid.duck.opengl.util.resources.shader.ShaderProgram;

import java.io.IOException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.function.BiConsumer;

/**
 * An ordered, mutable list of {@link ShaderVariable} bindings associated with a renderer.
 *
 * <p>Call {@link #updateForRender(RenderContext, ShaderProgram)} each frame (after {@code glUseProgram})
 * to push the current value of every registered variable to its corresponding GLSL uniform.
 * Variables are applied in insertion order.</p>
 */
public class ShaderVariables extends AbstractList<ShaderVariable<?>> {

    /** Default constructor. */
    public ShaderVariables() {}

    private final ArrayList<ShaderVariable<?>> variables = new ArrayList<>();

    @Override
    public int size() {
        return variables.size();
    }

    @Override
    public ShaderVariable<?> get(int index) {
        return variables.get(index);
    }

    @Override
    public ShaderVariable<?> set(int index, ShaderVariable element) {
        return super.set(index, element);
    }

    @Override
    public boolean add(ShaderVariable<?> shaderVariable) {
        return variables.add(shaderVariable);
    }

    @Override
    public ShaderVariable<?> remove(int index) {
        return variables.remove(index);
    }

    /**
     * Push the current value of every registered variable to the corresponding uniform in {@code program}.
     * Must be called while the program is active (after {@link ShaderProgram#use(com.asteroid.duck.opengl.util.RenderContext)}).
     *
     * @param ctx     the current render context; passed to any context-sensitive providers
     * @param program the shader program whose uniforms should be updated
     */
    public void updateForRender(RenderContext ctx, ShaderProgram program) {
        for(var var : variables) {
            var.accept(ctx, program);
        }
    }
}
