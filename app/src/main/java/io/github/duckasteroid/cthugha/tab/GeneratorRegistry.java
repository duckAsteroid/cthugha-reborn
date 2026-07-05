package io.github.duckasteroid.cthugha.tab;

import io.github.duckasteroid.cthugha.params.action.AbstractAction;
import io.github.duckasteroid.cthugha.params.AbstractNode;
import io.github.duckasteroid.cthugha.params.AbstractValue;
import io.github.duckasteroid.cthugha.params.Node;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.values.EnumParameter;
import io.github.duckasteroid.cthugha.params.values.StringParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Manages the available {@link TabGenerator} implementations and the one currently active.
 *
 * <h2>Param tree layout</h2>
 * <pre>
 * Translate Source (this node)
 *   ├── Generator  [EnumParameter — carousel/dropdown of generator names]
 *   ├── &lt;active generator node&gt;  [the selected TabGenerator, swapped on change]
 *   ├── Presets    [AllPresetsNode — flat across all generators, if tabStore provided]
 *   ├── Save Name  [StringParameter, if tabStore provided]
 *   └── Save       [Action, if tabStore provided]
 * </pre>
 *
 * <h2>Live updates</h2>
 * <p>Two callbacks drive the rendering layer:</p>
 * <ul>
 *   <li>{@link #setOnNewGeneratorSelected} — fired when the user picks a different generator.
 *       The caller should randomise + regenerate the translation map.</li>
 *   <li>{@link #setOnRegenerateNeeded} — fired when any parameter of the current generator
 *       changes.  The caller should regenerate using the current (unchanged) params.</li>
 * </ul>
 * <p>Call {@link #beginBatch()}/{@link #endBatch()} around bulk param writes (e.g. preset load)
 * to suppress per-value regeneration triggers.</p>
 *
 * <h2>Tree change events</h2>
 * <p>{@link #setOnTreeChanged} is called whenever the active generator changes, so the caller
 * can broadcast a {@code treeChanged} SSE event and let remote clients re-fetch the param tree.</p>
 */
public class GeneratorRegistry extends AbstractNode {

    private static final Logger LOG = LoggerFactory.getLogger(GeneratorRegistry.class);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final List<TabGenerator> generators;
    private final EnumParameter<String> generatorSelector;
    private TabGenerator selected;
    private int selectedIndex = 0;

    private final AllPresetsNode presetsNode;
    private final StringParameter saveName;
    private final AbstractAction saveAction;

    private Runnable onRegenerateNeeded;
    private Runnable onNewGeneratorSelected;
    private Runnable onTreeChanged;

    private final List<AbstractValue> watchedParams = new ArrayList<>();
    private volatile boolean batchMode = false;
    private boolean changingSelector = false;

    private final Runnable paramChangeHandler = () -> {
        if (!batchMode && onRegenerateNeeded != null) onRegenerateNeeded.run();
    };

    public GeneratorRegistry() {
        this(null);
    }

    public GeneratorRegistry(TabStore tabStore) {
        super("Translate Source");
        withUiHint(UiHint.ICON, "layers");

        generators = StreamSupport
                .stream(ServiceLoader.load(TabGenerator.class).spliterator(), false)
                .sorted(Comparator.comparing(g -> g.getClass().getSimpleName()))
                .collect(Collectors.toList());

        List<String> names = generators.stream()
                .map(g -> g.getClass().getSimpleName())
                .collect(Collectors.toList());
        generatorSelector = new EnumParameter<>("Generator", names);
        generatorSelector.withUiHint(UiHint.CONTROL_TYPE, UiHint.CAROUSEL);
        generatorSelector.withUiHint(UiHint.ICON, "cpu");

        selected = generators.get(0);

        if (tabStore != null) {
            presetsNode = new AllPresetsNode(tabStore, generators);
            saveName = new StringParameter("Save Name", "");
            saveAction = buildSaveAction(tabStore);
        } else {
            presetsNode = null;
            saveName = null;
            saveAction = null;
        }

        generatorSelector.addChangeListener(() -> {
            if (changingSelector) return;
            applySelection(generatorSelector.getValue().intValue(), true);
        });

        rebuildChildren();
        watchParamChanges(selected);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void setOnRegenerateNeeded(Runnable r)    { this.onRegenerateNeeded = r; }
    public void setOnNewGeneratorSelected(Runnable r) { this.onNewGeneratorSelected = r; }
    public void setOnTreeChanged(Runnable r)          { this.onTreeChanged = r; }

    /**
     * Suppresses per-parameter regeneration callbacks during bulk param writes.
     * Always pair with {@link #endBatch()}.
     */
    public void beginBatch() { batchMode = true; }

    /** Re-enables per-parameter regeneration callbacks. */
    public void endBatch()   { batchMode = false; }

    /** Generates a translation map using the current generator after randomising its params. */
    public TabMapping generate(int width, int height, Random rng) {
        beginBatch();
        selected.randomise(rng);
        endBatch();
        return selected.generate(width, height, rng);
    }

    /** Generates a translation map using the current generator's current param values (no randomise). */
    public TabMapping generateCurrent(int width, int height, Random rng) {
        return selected.generate(width, height, rng);
    }

    /** Picks a random generator and fires {@link #setOnNewGeneratorSelected}. */
    public void selectRandom(Random rng) {
        applySelection(rng.nextInt(generators.size()), true);
    }

    /** Steps to the next (+1) or previous (-1) generator and fires {@link #setOnNewGeneratorSelected}. */
    public void stepSelection(int delta) {
        int n = generators.size();
        applySelection(Math.floorMod(selectedIndex + delta, n), true);
    }

    /**
     * Selects the generator whose simple class name matches {@code simpleName}, without
     * triggering a regeneration. Used when restoring a saved preset (the caller supplies
     * the pre-built buffer).
     */
    public void selectBySimpleName(String simpleName) {
        for (int i = 0; i < generators.size(); i++) {
            if (generators.get(i).getClass().getSimpleName().equals(simpleName)) {
                applySelection(i, false);
                return;
            }
        }
        throw new IllegalArgumentException("No TabGenerator with simple name: " + simpleName);
    }

    public String getLastGenerated() {
        return selected != null ? selected.getClass().getSimpleName() : "";
    }

    public List<TabGenerator> getSources() {
        return Collections.unmodifiableList(generators);
    }

    public TabGenerator getSelected() {
        return selected;
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    /**
     * Core selection change: updates {@link #selected}, rebuilds tree children, syncs the
     * {@link #generatorSelector} param, and optionally fires the regeneration callback.
     */
    private void applySelection(int idx, boolean triggerRegenerate) {
        TabGenerator next = generators.get(idx);
        boolean changed = (next != selected);

        selected = next;
        selectedIndex = idx;

        // Sync selector param without re-entering this method
        changingSelector = true;
        generatorSelector.setValue(idx);
        changingSelector = false;

        if (changed) {
            watchParamChanges(selected);
            rebuildChildren();
            if (onTreeChanged != null) onTreeChanged.run();
        }

        if (triggerRegenerate && onNewGeneratorSelected != null) {
            onNewGeneratorSelected.run();
        }
    }

    private void rebuildChildren() {
        List<Node> current = getChildren().collect(Collectors.toList());
        current.forEach(this::removeChild);
        addChild(generatorSelector);
        addChild(selected);
        if (presetsNode != null) addChild(presetsNode);
        if (saveName != null) addChild(saveName);
        if (saveAction != null) addChild(saveAction);
    }

    private void watchParamChanges(TabGenerator gen) {
        watchedParams.forEach(p -> p.removeChangeListener(paramChangeHandler));
        watchedParams.clear();
        collectValues(gen, watchedParams);
        watchedParams.forEach(p -> p.addChangeListener(paramChangeHandler));
    }

    private void collectValues(Node node, List<AbstractValue> result) {
        if (node instanceof AbstractValue av) {
            result.add(av);
        } else {
            node.getChildren().forEach(child -> collectValues(child, result));
        }
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
                    if (presetsNode != null) presetsNode.refresh();
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
}
