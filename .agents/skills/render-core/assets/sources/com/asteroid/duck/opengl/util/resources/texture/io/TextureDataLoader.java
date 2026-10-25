package com.asteroid.duck.opengl.util.resources.texture.io;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Interface for loading texture data from a file.
 * <p>
 * This interface defines a method for loading image data from a file specified by a {@link Path} object.
 * The image data is returned as an {@link TextureData} object, which contains the pixel data and metadata.
 * <p>
 * The {@link ImageLoadingOptions} parameter allows for customisation of the image loading process, such as specifying
 * the desired image format or enabling/disabling flipping etc.
 */
public interface TextureDataLoader {
    /**
     * Loads image data from the specified file path using the given image options.
     *
     * @param path The path to the image file.
     * @param options The image loading options.
     * @return The loaded image data.
     * @throws IOException If an error occurs while reading the image file.
     */
    TextureData load(String path, ImageLoadingOptions options) throws IOException;
}
