package io.github.duckasteroid.cthugha.dump;

import com.asteroid.duck.opengl.util.keys.KeyRegistry;
import io.github.duckasteroid.cthugha.params.Node;

import java.io.IOException;
import java.io.Writer;

public interface DumpFormat {
    void dump(Node root, KeyRegistry keys, Writer out) throws IOException;
}
