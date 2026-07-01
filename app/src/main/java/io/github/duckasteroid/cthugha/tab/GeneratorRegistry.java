package io.github.duckasteroid.cthugha.tab;

import io.github.duckasteroid.cthugha.params.AbstractAction;
import io.github.duckasteroid.cthugha.params.AbstractNode;
import io.github.duckasteroid.cthugha.params.Node;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.values.StringParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.*;

/**
 * Loads all {@link TabGenerator} implementations via {@link ServiceLoader} and manages selection.
 *
 * <p>Generators are discovered automatically from {@code META-INF/services/} entries written
 * at compile time by the {@code @AutoService} annotation processor — no central list to maintain.
 * Instances are sorted by simple class name for consistent step-through ordering.</p>
 *
 * <p>When constructed with a {@link TabStore}, each generator gains a {@link SavedPresetsNode}
 * child, and a "Save" action is added as the first child of this registry node.</p>
 */
public class GeneratorRegistry extends AbstractNode {

    private static final Logger LOG = LoggerFactory.getLogger(GeneratorRegistry.class);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final List<TabGenerator> generators;
    private TabGenerator selected;
    private int selectedIndex = -1;
    private final StringParameter saveName = new StringParameter("Save Name", "");

    /** Constructs without persistence support (no SavedPresetsNode, no Save action). */
    public GeneratorRegistry() {
        this(null);
    }

    /**
     * Constructs with persistence support.  Each discovered generator gets a
     * {@link SavedPresetsNode} child populated from {@code tabStore}, and a "Save" action
     * is prepended to this node's children.
     */
    public GeneratorRegistry(TabStore tabStore) {
        super("Translate Source");
        withUiHint(UiHint.ICON, "layers");
        generators = StreamSupport
                .stream(ServiceLoader.load(TabGenerator.class).spliterator(), false)
                .sorted(Comparator.comparing(g -> g.getClass().getSimpleName()))
                .collect(Collectors.toList());

        if (tabStore != null) {
            generators.forEach(gen -> gen.addChild(new SavedPresetsNode(tabStore, gen)));
        }

        List<Node> children = new ArrayList<>();
        if (tabStore != null) {
            children.add(saveName);
            children.add(buildSaveAction(tabStore));
        }
        children.addAll(generators);
        initChildren(children);
    }

    private AbstractAction buildSaveAction(TabStore tabStore) {
        AbstractAction save = new AbstractAction("Save", ctx -> {
            if (ctx instanceof TabActionContext tctx) {
                TabGenerator sel = tctx.registry().getSelected();
                if (sel == null) { tctx.notify("no translation active"); return; }
                String typed = saveName.getValue().trim();
                String name = typed.isEmpty()
                        ? sel.getClass().getSimpleName() + "_" + LocalDateTime.now().format(TIMESTAMP)
                        : typed;
                try {
                    tabStore.save(sel, name, tctx.currentBuffer(), tctx.resolution());
                    sel.getChildren()
                       .filter(n -> n instanceof SavedPresetsNode)
                       .findFirst()
                       .map(n -> (SavedPresetsNode) n)
                       .ifPresent(SavedPresetsNode::refresh);
                    saveName.setValue("");
                    tctx.notify("saved: " + name);
                } catch (IOException e) {
                    LOG.error("Failed to save translation preset", e);
                    tctx.notify("save failed");
                }
            }
        });
        save.withUiHint(UiHint.ICON, "save");
        return save;
    }

    public TabMapping generate(int width, int height, boolean newSource, Random rng) {
        if (selected == null || newSource) {
            selectedIndex = rng.nextInt(generators.size());
            selected = generators.get(selectedIndex);
        }
        selected.randomise(rng);
        return selected.generate(width, height, rng);
    }

    public TabMapping step(int delta, int width, int height, Random rng) {
        if (selectedIndex < 0) selectedIndex = 0;
        selectedIndex = Math.floorMod(selectedIndex + delta, generators.size());
        selected = generators.get(selectedIndex);
        selected.randomise(rng);
        return selected.generate(width, height, rng);
    }

    public String getLastGenerated() {
        return selected != null ? selected.toString() : "";
    }

    public List<TabGenerator> getSources() {
        return Collections.unmodifiableList(generators);
    }

    public TabGenerator getSelected() {
        return selected;
    }

    /**
     * Selects the canonical generator whose class simple name matches {@code simpleName}
     * without randomising its parameters. Used when restoring a saved preset.
     *
     * @throws IllegalArgumentException if no generator has that simple name
     */
    public void selectBySimpleName(String simpleName) {
        for (int i = 0; i < generators.size(); i++) {
            if (generators.get(i).getClass().getSimpleName().equals(simpleName)) {
                selectedIndex = i;
                selected = generators.get(i);
                return;
            }
        }
        throw new IllegalArgumentException("No TabGenerator with simple name: " + simpleName);
    }
}
