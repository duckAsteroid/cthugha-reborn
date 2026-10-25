package com.asteroid.duck.opengl.util.resources.buffer;

import com.asteroid.duck.opengl.util.resources.bound.Binder;
import com.asteroid.duck.opengl.util.resources.bound.BindingException;
import com.google.auto.service.AutoService;

import static org.lwjgl.opengl.GL30.glBindVertexArray;

@SuppressWarnings("rawtypes")
@AutoService(Binder.class)
public class VertexArrayObjectBinder implements Binder<VertexArrayObject> {
    @Override
    public Class<VertexArrayObject> resourceType() {
        return VertexArrayObject.class;
    }

    @Override
    public void bind(VertexArrayObject vao) throws BindingException {
        glBindVertexArray(vao.id());
    }

    @Override
    public VertexArrayObject unbind(VertexArrayObject resource) throws BindingException {
        glBindVertexArray(0);
        return null;
    }
}
