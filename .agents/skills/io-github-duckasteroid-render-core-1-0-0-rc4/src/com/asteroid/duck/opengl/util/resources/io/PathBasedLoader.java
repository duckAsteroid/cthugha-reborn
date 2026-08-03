package com.asteroid.duck.opengl.util.resources.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resource loader that loads resources from a specified base file system path.
 */
public class PathBasedLoader implements Loader {
    private final Path basePath;

    /**
     * Create a loader rooted at the given file-system path.
     *
     * @param basePath the directory from which relative resource paths are resolved;
     *                 must exist and be readable at the time resources are opened
     */
    public PathBasedLoader(Path basePath) {
        this.basePath = basePath;
    }

    @Override
    public String describe(String relativePath) {
        return "Path["+basePath.resolve(relativePath).toAbsolutePath()+"]";
    }

    @Override
    public InputStream open(String relativePath) throws IOException {
        Path fullPath = basePath.resolve(relativePath);
        return Files.newInputStream(fullPath);
    }

    @Override
    public boolean exists(String relativePath) throws IOException {
        return Files.exists(basePath.resolve(relativePath));
    }

    @Override
    public Loader atPath(String relativePath) {
        Path newPath = basePath.resolve(relativePath);
        return new PathBasedLoader(newPath);
    }
}
