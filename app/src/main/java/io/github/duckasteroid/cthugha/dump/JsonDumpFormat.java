package io.github.duckasteroid.cthugha.dump;

import com.asteroid.duck.opengl.util.keys.KeyAction;
import com.asteroid.duck.opengl.util.keys.KeyRegistry;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.duckasteroid.cthugha.params.Node;
import io.github.duckasteroid.cthugha.remote.ParamSerializer;

import java.io.IOException;
import java.io.Writer;

public class JsonDumpFormat implements DumpFormat {

    private final ParamSerializer serializer = new ParamSerializer();

    @Override
    public void dump(Node root, KeyRegistry keys, Writer out) throws IOException {
        ObjectNode doc = serializer.getMapper().createObjectNode();
        doc.set("params", serializer.serialize(root));
        if (keys != null) {
            ArrayNode bindings = serializer.getMapper().createArrayNode();
            for (KeyAction action : keys) {
                ObjectNode entry = serializer.getMapper().createObjectNode();
                entry.put("key", KeyFormatUtil.format(action.getCombination()));
                entry.put("description", action.getDescription());
                bindings.add(entry);
            }
            doc.set("keyBindings", bindings);
        }
        serializer.getMapper()
                  .writerWithDefaultPrettyPrinter()
                  .writeValue(out, doc);
    }
}
