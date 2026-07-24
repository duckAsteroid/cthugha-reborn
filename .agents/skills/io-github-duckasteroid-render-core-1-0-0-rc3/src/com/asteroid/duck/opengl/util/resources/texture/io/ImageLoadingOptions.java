package com.asteroid.duck.opengl.util.resources.texture.io;

import com.asteroid.duck.opengl.util.resources.texture.DataFormat;

/**
 * Represents various options for how we load images file data into textures.
 *
 * @param flipY If true, the image will be flipped vertically during loading.
 * @param singleLine If true, the image will be loaded as a single line texture.
 * @param dataFormat The type of the image data (e.g., RGBA, RGB, etc.).
 */
public record ImageLoadingOptions(boolean flipY, boolean singleLine, DataFormat dataFormat) {
    /**
     * A default instance with flipY set to true, singleLine set to false, and type set to RGBA.
     */
    public static final ImageLoadingOptions DEFAULT = new ImageLoadingOptions(true, false, DataFormat.RGBA);

    /**
     * Returns a new instance with the flipY flag toggled.
     * If the current flipY flag is true, the returned instance will have flipY set to false.
     * If the current flipY flag is false, this instance will be returned.
     *
     * @return A new instance with the flipY flag toggled.
     */
    public ImageLoadingOptions withNoFlip() {
        if (flipY) {
            return new ImageLoadingOptions(false, singleLine, dataFormat);
        }
        return this;
    }

    /**
     * Returns a new instance with the singleLine flag toggled.
     * If the current singleLine flag is false, the returned instance will have singleLine set to true.
     * If the current singleLine flag is true, this instance will be returned.
     *
     * @return A new instance with the singleLine flag toggled.
     */
    public ImageLoadingOptions withSingleLine() {
        if (!singleLine) {
            return new ImageLoadingOptions(flipY, true, dataFormat);
        }
        return this;
    }

    /**
     * Returns a new instance with the type set to the specified value.
     * If the current type is equal to the specified value, this instance will be returned.
     * If the current type is not equal to the specified value, the returned instance will have the type set to the specified value.
     *
     * @param dataFormat The new type for the new instance.
     * @return A new instance with the type set to the specified value.
     */
    public ImageLoadingOptions withType(DataFormat dataFormat) {
        if (this.dataFormat != dataFormat) {
            return new ImageLoadingOptions(flipY, singleLine, dataFormat);
        }
        return this;
    }
}
