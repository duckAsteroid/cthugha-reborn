package com.asteroid.duck.opengl.util.resources.texture.io;

import java.awt.*;
import java.nio.ByteBuffer;

/**
 * This class represents the image data, including the pixel buffer and its dimensions.
 *
 * @param buffer The pixel buffer containing the image data.
 * @param size   The dimensions of the image.
 */
public record TextureData(ByteBuffer buffer, Dimension size) {

    /**
     * Calculates the total number of pixels in the image.
     *
     * @return The total number of pixels in the image.
     */
    public int totalPixelCount() {
        return size.width * size.height;
    }
}
