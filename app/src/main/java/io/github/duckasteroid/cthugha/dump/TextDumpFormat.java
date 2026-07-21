package io.github.duckasteroid.cthugha.dump;

import com.asteroid.duck.opengl.util.keys.KeyAction;
import com.asteroid.duck.opengl.util.keys.KeyRegistry;
import io.github.duckasteroid.cthugha.params.AbstractValue;
import io.github.duckasteroid.cthugha.params.action.Action;
import io.github.duckasteroid.cthugha.params.Node;
import io.github.duckasteroid.cthugha.params.StringValue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class TextDumpFormat implements DumpFormat {

    @Override
    public void dump(Node root, KeyRegistry keys, Writer out) throws IOException {
        PrintWriter pw = new PrintWriter(out);

        // description (node path for INI bindings) → formatted key combos
        Map<String, List<String>> descToKeys = buildDescMap(keys);
        Set<String> usedDescs = new HashSet<>();

        dumpNode(pw, root, "", "", descToKeys, usedDescs);

        // Print any bindings whose description didn't match a tree path
        if (keys != null) {
            List<KeyAction> unmapped = new ArrayList<>();
            for (KeyAction action : keys) {
                if (!usedDescs.contains(action.getDescription())) {
                    unmapped.add(action);
                }
            }
            if (!unmapped.isEmpty()) {
                pw.println();
                pw.println("Other key bindings:");
                for (KeyAction action : unmapped) {
                    pw.printf("  %-24s  %s%n",
                            KeyFormatUtil.format(action.getCombination()),
                            action.getDescription());
                }
            }
        }

        pw.flush();
    }

    private void dumpNode(PrintWriter pw, Node node, String indent, String parentPath,
                          Map<String, List<String>> descToKeys, Set<String> usedDescs) {
        // Root node is excluded from paths (matches ParamSerializer convention)
        String path = parentPath.isEmpty() ? "" : (parentPath + "/" + node.getName());
        // For root's direct children, path = just the node name
        if (parentPath.equals("root")) {
            path = node.getName();
        }

        String keysStr = keysAnnotation(path, descToKeys, usedDescs);

        if (node instanceof Action) {
            pw.printf("%s[action] %s%s%n", indent, node.getName(), keysStr);
        } else if (node instanceof StringValue sv) {
            pw.printf("%s%s = \"%s\"%s%n", indent, node.getName(), sv.getValue(), keysStr);
        } else if (node instanceof AbstractValue av) {
            pw.printf("%s%s = %s [%s..%s]%s%n",
                    indent, node.getName(), av.getValue(), av.getMin(), av.getMax(), keysStr);
        } else {
            pw.printf("%s%s%s%n", indent, node.getName(), keysStr);
            String childParent = parentPath.isEmpty() ? "root" : path;
            node.getChildren().forEach(child ->
                    dumpNode(pw, child, indent + "  ", childParent, descToKeys, usedDescs));
        }
    }

    private String keysAnnotation(String path, Map<String, List<String>> descToKeys, Set<String> usedDescs) {
        if (path.isEmpty()) return "";
        List<String> combos = descToKeys.get(path);
        if (combos == null || combos.isEmpty()) return "";
        usedDescs.add(path);
        return "  <-- " + String.join(", ", combos);
    }

    private Map<String, List<String>> buildDescMap(KeyRegistry keys) {
        if (keys == null) return Map.of();
        Map<String, List<String>> map = new HashMap<>();
        for (KeyAction action : keys) {
            map.computeIfAbsent(action.getDescription(), k -> new ArrayList<>())
               .add(KeyFormatUtil.format(action.getCombination()));
        }
        return map;
    }
}
