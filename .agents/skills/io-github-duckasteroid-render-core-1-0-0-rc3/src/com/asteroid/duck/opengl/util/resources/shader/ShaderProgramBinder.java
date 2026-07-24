package com.asteroid.duck.opengl.util.resources.shader;

import com.asteroid.duck.opengl.util.resources.bound.Binder;
import com.asteroid.duck.opengl.util.resources.bound.BindingException;
import com.google.auto.service.AutoService;

import static org.lwjgl.opengl.GL20.glUseProgram;

/**
 * {@link Binder} implementation for {@link ShaderProgram} that calls {@code glUseProgram}
 * to install/remove a shader program as the active program on the GL context.
 * Registered automatically via {@link AutoService}.
 */
@SuppressWarnings("rawtypes")
@AutoService(Binder.class)
public class ShaderProgramBinder implements Binder<ShaderProgram> {

    /** Default constructor. */
    public ShaderProgramBinder() {}
    @Override
    public Class<ShaderProgram> resourceType() {
        return ShaderProgram.class;
    }

    @Override
    public void bind(ShaderProgram program) throws BindingException {
        glUseProgram(program.id());
    }

    @Override
    public ShaderProgram unbind(ShaderProgram resource) throws BindingException {
        glUseProgram(0);
        return null;
    }
}
