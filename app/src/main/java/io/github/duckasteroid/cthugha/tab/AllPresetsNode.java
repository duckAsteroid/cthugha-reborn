package io.github.duckasteroid.cthugha.tab;

import io.github.duckasteroid.cthugha.params.AbstractNode;
import io.github.duckasteroid.cthugha.params.ContainerNode;
import io.github.duckasteroid.cthugha.params.Node;
import io.github.duckasteroid.cthugha.params.UiHint;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A presets browser that lists saved tabs for every generator in one place.
 *
 * <p>Mounted at the registry root so presets are accessible without navigating into a specific
 * generator. Generators with no saved presets are omitted. Each generator that has presets
 * appears as a sub-container named by its simple class name.</p>
 *
 * <p>Call {@link #refresh()} after any save or delete.</p>
 */
public class AllPresetsNode extends AbstractNode {

    private final TabStore store;
    private final List<TabGenerator> generators;

    public AllPresetsNode(TabStore store, List<TabGenerator> generators) {
        super("Presets");
        withUiHint(UiHint.ICON, "bookmark");
        this.store = store;
        this.generators = generators;
        refresh();
    }

    public void refresh() {
        List<Node> current = getChildren().collect(Collectors.toList());
        current.forEach(this::removeChild);
        for (TabGenerator gen : generators) {
            List<TabConfig> configs = store.listFor(gen);
            if (!configs.isEmpty()) {
                ContainerNode genNode = new ContainerNode(gen.getClass().getSimpleName());
                configs.forEach(cfg -> genNode.addChild(new TabConfigNode(cfg, gen, this::refresh)));
                addChild(genNode);
            }
        }
    }
}
