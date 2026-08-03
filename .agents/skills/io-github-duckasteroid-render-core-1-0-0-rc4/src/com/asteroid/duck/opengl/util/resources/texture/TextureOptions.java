package com.asteroid.duck.opengl.util.resources.texture;

import java.awt.*;

/**
 * Options for a newly created texture
 * @param dataFormat The format of the texture
 * @param filter filtering strategy used for zooming
 * @param wrap does the texture repeat
 * @see TextureFactory#createTexture(Rectangle, Dimension, TextureOptions)
 */
public record TextureOptions(DataFormat dataFormat, Filter filter, Wrap wrap) {
}
