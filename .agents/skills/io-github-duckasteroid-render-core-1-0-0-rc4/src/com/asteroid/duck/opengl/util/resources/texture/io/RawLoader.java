package com.asteroid.duck.opengl.util.resources.texture.io;

import com.asteroid.duck.opengl.util.resources.io.Loader;
import org.lwjgl.BufferUtils;

import java.awt.*;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A class that implements the TextureDataLoader interface and loads texture data from a raw file.
 * It always creates images of the same "size" (whatever it's initialised with)
 */
public class RawLoader implements TextureDataLoader {
    /**
     * The size of the texture data.
     */
    private final Dimension size;
    private final Loader loader;
    /**
     * Create a loader that reads raw RGBA bytes and presents them as textures of a fixed size.
     *
     * @param size   the width and height of every texture produced by this loader; the raw file must
     *               contain exactly {@code width * height * 4} bytes (RGBA)
     * @param loader the underlying resource loader used to open the raw file by relative path
     */
    public RawLoader(Dimension size, Loader loader) {
        this.size = size;
        this.loader = loader;
    }

    /**
     * Loads texture data from the specified file path and options.
     *
     * @param path    The file path of the raw texture data.
     * @param options The image options for loading the texture data. These are ignored in this implementation
     * @return The loaded image data.
     * @throws IOException If an error occurs while reading the file.
     */
    @Override
    public TextureData load(String path, ImageLoadingOptions options) throws IOException {
        try(var is = loader.open(path)) {
            byte[] bytes = is.readAllBytes();
            ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
            buffer.put(bytes);
            buffer.flip();
            return new TextureData(buffer, size);
        }
    }
}
