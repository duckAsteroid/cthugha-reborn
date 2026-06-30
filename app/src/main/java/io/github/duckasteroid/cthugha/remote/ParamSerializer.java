package io.github.duckasteroid.cthugha.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.duckasteroid.cthugha.params.AbstractValue;
import io.github.duckasteroid.cthugha.params.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ParamSerializer {

    private final ObjectMapper mapper = new ObjectMapper();

    public ObjectMapper getMapper() {
        return mapper;
    }

    public ObjectNode serialize(Node node) {
        ObjectNode obj = mapper.createObjectNode();
        obj.put("name", node.getName());
        obj.put("type", node.getNodeType().name());

        if (node instanceof AbstractValue value) {
            obj.put("value", value.getValue().doubleValue());
            obj.put("min", value.getMin().doubleValue());
            obj.put("max", value.getMax().doubleValue());
            obj.put("controlled", value.isControlled());
            obj.put("uiHint", value.getUiHint().name());
        } else {
            ArrayNode children = mapper.createArrayNode();
            node.getChildren().forEach(child -> children.add(serialize(child)));
            obj.set("children", children);
        }

        return obj;
    }

    /**
     * Returns the slash-delimited path from the root's first child down to this node.
     * Excludes the root node itself. Parents must be set via addChild for this to work;
     * falls back gracefully when parent references are not wired.
     */
    public String pathOf(Node node) {
        List<String> parts = new ArrayList<>();
        parts.add(node.getName());
        Node current = node;
        while (true) {
            try {
                if (!current.hasParent()) break;
            } catch (NullPointerException e) {
                // parent Optional not initialized (tree built via initFields, not addChild)
                break;
            }
            current = current.getParent();
            if (current == null) break;
            parts.add(current.getName());
        }
        // parts is bottom-up: [node, ..., root]. Reverse, then skip root.
        Collections.reverse(parts);
        if (parts.size() > 1) {
            return String.join("/", parts.subList(1, parts.size()));
        }
        return parts.isEmpty() ? "" : parts.get(0);
    }

    public ObjectNode buildChangeEvent(String path, AbstractValue value) {
        ObjectNode event = mapper.createObjectNode();
        event.put("path", path);
        event.put("value", value.getValue().doubleValue());
        event.put("controlled", value.isControlled());
        return event;
    }
}
