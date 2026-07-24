package com.asteroid.duck.opengl.util.resources.texture;

import com.asteroid.duck.opengl.util.resources.bound.Binder;
import com.asteroid.duck.opengl.util.resources.bound.BindingException;
import com.google.auto.service.AutoService;

import static org.lwjgl.opengl.GL11.glBindTexture;

@SuppressWarnings("rawtypes")
@AutoService(Binder.class)
public class TextureBinder implements Binder<Texture> {
    @Override
    public Class<Texture> resourceType() {
        return Texture.class;
    }

    @Override
    public void bind(Texture tex) throws BindingException {
        glBindTexture(tex.dimensions.openGlCode(), tex.getId());
    }

    @Override
    public Texture unbind(Texture tex) throws BindingException {
        glBindTexture(tex.dimensions.openGlCode(), 0);
        return null;
    }
}
