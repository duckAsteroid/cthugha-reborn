package io.github.duckasteroid.cthugha.tab;

import io.github.duckasteroid.cthugha.params.AbstractNode;
import io.github.duckasteroid.cthugha.params.Node;
import io.github.duckasteroid.cthugha.params.UiHint;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A container node whose children are {@link TabConfigNode}s for every saved preset
 * belonging to one {@link TabGenerator}.
 *
 * <p>Mounted as a child of each generator node by {@link GeneratorRegistry} when a
 * {@link TabStore} is provided.  Call {@link #refresh()} after any save or delete to
 * re-scan disk and rebuild the child list.</p>
 */
public class SavedPresetsNode extends AbstractNode {

    private final TabStore store;
    private final TabGenerator generator;

    public SavedPresetsNode(TabStore store, TabGenerator generator) {
        super("Presets");
        this.store = store;
        this.generator = generator;
        withUiHint(UiHint.ICON, "bookmark");
        refresh();
    }

    /** Re-scans disk and rebuilds the child list from the current saved presets. */
    public void refresh() {
        List<Node> current = getChildren().collect(Collectors.toList());
        current.forEach(this::removeChild);
        store.listFor(generator)
             .forEach(config -> addChild(new TabConfigNode(config, generator, this::refresh)));
    }
}
