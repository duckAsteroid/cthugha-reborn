package com.asteroid.duck.opengl.util.resources.io;

import java.io.IOException;
import java.io.InputStream;
/**
 * Interface for loading external data as input streams.
 */
public interface Loader {
    /**
     * Describe a resource for debugging. There is no guarantee the resource exists!
     * @param relativePath the path to the resource
     * @return a descriptive string for that resource
     */
    String describe(String relativePath);

    /**
     * Open a new input stream to the given data
     * @param relativePath the relative path to the data
     * @return an input stream
     * @throws IOException If the resource does not exist, or there is a problem reading from it
     */
    InputStream open(String relativePath) throws IOException;

    /**
     * Create a new loader that accesses data from a given relative path
     * @param relativePath the path, this will be added to this loader path
     * @return a new loader that works in the given relative path
     */
    Loader atPath(String relativePath);

    /**
     * Does the named data resource exist?
     * @param relativePath the path to the data
     * @return true if it exists
     * @throws IOException if there was a problem accessing the data
     */
    boolean exists(String relativePath) throws IOException;
}
