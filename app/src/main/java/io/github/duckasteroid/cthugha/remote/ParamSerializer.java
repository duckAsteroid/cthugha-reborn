package io.github.duckasteroid.cthugha.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.duckasteroid.cthugha.binding.BindingSystem;
import io.github.duckasteroid.cthugha.binding.EdgeTriggeredBinding;
import io.github.duckasteroid.cthugha.params.AbstractValue;
import io.github.duckasteroid.cthugha.params.AnimationBindingView;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.action.Action;
import io.github.duckasteroid.cthugha.params.Node;
import io.github.duckasteroid.cthugha.params.StringValue;
import io.github.duckasteroid.cthugha.params.values.EnumParameter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ParamSerializer {

    private final ObjectMapper mapper = new ObjectMapper();
    /** Nullable: only needed to embed the {@code triggers} array; callers with no bindings to report (e.g. {@code JsonDumpFormat}) just get a serializer that never attaches one. */
    private final BindingSystem bindings;

    public ParamSerializer() {
        this(null);
    }

    public ParamSerializer(BindingSystem bindings) {
        this.bindings = bindings;
    }

    public ObjectMapper getMapper() {
        return mapper;
    }

    public ObjectNode serialize(Node node) {
        ObjectNode obj = mapper.createObjectNode();
        obj.put("name", node.getName());
        obj.put("type", node.getNodeType().name());

        String description = node.getDescription();
        if (description != null && !description.isBlank()) {
            obj.put("description", description);
        }

        Map<String, String> hints = node.getUiHints();
        if (!hints.isEmpty()) {
            ObjectNode hintsNode = mapper.createObjectNode();
            hints.forEach(hintsNode::put);
            obj.set("uiHints", hintsNode);
        }

        if (node instanceof Action) {
            // leaf — no children, no value; type=ACTION is sufficient for the client
            attachTriggers(obj, node, false);
        } else if (node instanceof StringValue sv) {
            obj.put("value", sv.getValue());
        } else if (node instanceof AbstractValue value) {
            obj.put("value", value.getValue().doubleValue());
            obj.put("min", value.getMin().doubleValue());
            obj.put("max", value.getMax().doubleValue());
            obj.put("controlled", value.isControlled());
            if (!value.isAnimatable()) {
                obj.put("animatable", false);
            }
            AnimationBindingView anim = value.getAnimationBinding();
            if (anim != null) {
                ObjectNode animNode = mapper.createObjectNode();
                animNode.put("script", anim.getScript());
                animNode.put("enabled", anim.isEnabled());
                if (anim.getCompileError() != null) {
                    animNode.put("compileError", anim.getCompileError());
                }
                obj.set("animation", animNode);
            }
            attachTriggers(obj, node, true);
            if (value instanceof EnumParameter<?> ep) {
                ArrayNode options = mapper.createArrayNode();
                List<String> labels = ep.getOptions();
                for (int i = 0; i < labels.size(); i++) {
                    ObjectNode opt = mapper.createObjectNode();
                    opt.put("label", labels.get(i));
                    String preview = ep.getPreviewUrl(i);
                    if (preview != null) opt.put("preview", preview);
                    String group = ep.getGroup(i);
                    if (group != null && !group.isBlank()) opt.put("group", group);
                    List<String> tags = ep.getTags(i);
                    if (tags != null && !tags.isEmpty()) {
                        ArrayNode tagsNode = mapper.createArrayNode();
                        tags.forEach(tagsNode::add);
                        opt.set("tags", tagsNode);
                    }
                    Double duration = ep.getDuration(i);
                    if (duration != null) opt.put("duration", duration);
                    List<EnumParameter.Chapter> chapters = ep.getChapters(i);
                    if (chapters != null && !chapters.isEmpty()) {
                        ArrayNode chaptersNode = mapper.createArrayNode();
                        for (EnumParameter.Chapter chapter : chapters) {
                            ObjectNode chapterNode = mapper.createObjectNode();
                            chapterNode.put("name", chapter.name());
                            chapterNode.put("start", chapter.start());
                            chapterNode.put("end", chapter.end());
                            chaptersNode.add(chapterNode);
                        }
                        opt.set("chapters", chaptersNode);
                    }
                    options.add(opt);
                }
                obj.set("options", options);
            }
        } else {
            ArrayNode children = mapper.createArrayNode();
            node.getChildren()
                .filter(Node::isRemoteAllowed)
                .forEach(child -> children.add(serialize(child)));
            obj.set("children", children);
        }

        return obj;
    }

    /**
     * Attaches a {@code "triggers"} array to {@code obj} listing every
     * {@link EdgeTriggeredBinding} currently targeting {@code node} — a node can have more than
     * one, unlike the 1:1 {@code animation} field. Omitted entirely when there are none, so
     * existing clients that don't look for it are unaffected. {@code includeValue} is {@code
     * true} for settable leaves (an {@link Action} target has nothing to set).
     */
    private void attachTriggers(ObjectNode obj, Node node, boolean includeValue) {
        if (bindings == null || !(node instanceof ParamNode pn)) return;
        List<EdgeTriggeredBinding> matches = bindings.findEdgeTriggeredBindingsFor(pn.getFullPath());
        if (matches.isEmpty()) return;
        ArrayNode arr = mapper.createArrayNode();
        for (EdgeTriggeredBinding binding : matches) {
            ObjectNode t = mapper.createObjectNode();
            t.put("name", binding.getName());
            t.put("condition", binding.condition.getValue());
            t.put("cooldown", binding.cooldown.value);
            t.put("enabled", binding.enabled.value);
            t.put("status", binding.status.getValue());
            if (includeValue) {
                t.put("value", binding.value.getValue());
            }
            if (binding.condition.getLastCompileError() != null) {
                t.put("compileError", binding.condition.getLastCompileError());
            }
            arr.add(t);
        }
        obj.set("triggers", arr);
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
        return buildChangeEvent(path, value.getValue().doubleValue(), value.isControlled());
    }

    public ObjectNode buildChangeEvent(String path, double value, boolean controlled) {
        ObjectNode event = mapper.createObjectNode();
        event.put("path", path);
        event.put("value", value);
        event.put("controlled", controlled);
        return event;
    }
}
