package com.asteroid.duck.opengl.util.resources.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * Resource loader that loads resources from the classpath relative to a given base class.
 */
public class ClasspathLoader implements Loader {
    private final Class<?> baseClass;
    private final String basePath;

    /**
     * Create a loader that resolves resource paths relative to a given class on the classpath.
     *
     * @param baseClass the class whose {@link Class#getResourceAsStream} is used for lookups;
     *                  determines both the classloader and the package-relative base directory
     * @param basePath  the path prefix prepended to every relative resource name; a trailing
     *                  {@code '/'} is added automatically if absent
     */
    public ClasspathLoader(Class<?> baseClass, String basePath) {
        this.baseClass = baseClass;
        this.basePath = basePath.endsWith("/") ? basePath : basePath + "/";
    }

    @Override
    public String describe(String relativePath) {
        return "Classpath["+baseClass.getName()+":"+basePath + relativePath+"]";
    }

    private InputStream tryOpen(String relativePath) {
        return baseClass.getResourceAsStream(basePath + relativePath);
    }

    @Override
    public InputStream open(String relativePath) throws IOException {
        InputStream stream = tryOpen(relativePath);
        if (stream == null) {
            throw new IOException("Resource not found: " + basePath + relativePath +" from class " + baseClass.getName());
        }
        return stream;
    }

    @Override
    public boolean exists(String relativePath) throws IOException {
        InputStream stream = tryOpen(relativePath);
        if  (stream == null) {
            return false;
        } else {
            stream.close();
            return true;
        }
    }

    @Override
    public Loader atPath(String relativePath) {
        return new ClasspathLoader(baseClass, basePath + relativePath);
    }
}
