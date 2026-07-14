package io.github.duckasteroid.cthugha.screenconfig;

import io.github.duckasteroid.cthugha.params.Node;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.action.AbstractAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * A container node representing one saved screen config.
 *
 * <p>Named after the config's display name, with two {@link AbstractAction} children:</p>
 * <ul>
 *   <li><b>Load</b> — applies the config's captured params to {@code treeRoot}.</li>
 *   <li><b>Delete</b> — removes the config from disk and refreshes the parent
 *       {@link ScreenConfigLibraryNode}.</li>
 * </ul>
 */
public class ScreenConfigNode extends ParamNode {

    private static final Logger LOG = LoggerFactory.getLogger(ScreenConfigNode.class);

    public ScreenConfigNode(ScreenConfig config, ScreenConfigStore store, Node treeRoot, Runnable onDeleted) {
        super(config.name);

        AbstractAction load = new AbstractAction("Load", ctx -> {
            store.load(config, treeRoot);
            ctx.notify("Loaded: " + config.name);
        });
        load.withUiHint(UiHint.ICON, "folder-open");
        load.withDescription("Restores this saved config's captured parameter values across "
            + "the whole tree (waves, animations, palette, tab generator, blur, etc.).");
        addChild(load);

        AbstractAction delete = new AbstractAction("Delete", ctx -> {
            try {
                store.delete(config);
                onDeleted.run();
                ctx.notify("Deleted: " + config.name);
            } catch (IOException e) {
                LOG.error("Failed to delete screen config '{}'", config.name, e);
                ctx.notify("Delete failed: " + config.name);
            }
        });
        delete.withUiHint(UiHint.ICON, "trash-2");
        delete.withDescription("Permanently removes this saved config from disk.");
        addChild(delete);
    }
}
