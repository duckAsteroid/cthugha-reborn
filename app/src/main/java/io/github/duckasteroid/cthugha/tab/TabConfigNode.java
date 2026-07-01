package io.github.duckasteroid.cthugha.tab;

import io.github.duckasteroid.cthugha.params.AbstractAction;
import io.github.duckasteroid.cthugha.params.AbstractNode;
import io.github.duckasteroid.cthugha.params.UiHint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * A container node representing one saved translation preset.
 *
 * <p>Named after the preset's display name, with two {@link AbstractAction} children:</p>
 * <ul>
 *   <li><b>Load</b> — applies the config's params to the canonical generator, loads (or
 *       regenerates) the {@code .tab} binary, and replaces the active buffer.</li>
 *   <li><b>Delete</b> — removes the preset folder from disk and refreshes the parent
 *       {@link SavedPresetsNode}.</li>
 * </ul>
 */
public class TabConfigNode extends AbstractNode {

    private static final Logger LOG = LoggerFactory.getLogger(TabConfigNode.class);

    public TabConfigNode(TabConfig config, TabGenerator generator, Runnable onDeleted) {
        super(config.name);

        AbstractAction load = new AbstractAction("Load", ctx -> {
            if (ctx instanceof TabActionContext tctx) {
                try {
                    tctx.registry().beginBatch();
                    TabBuffer buf = tctx.tabStore().load(config, generator, tctx.resolution(), tctx.rng());
                    tctx.registry().endBatch();
                    tctx.registry().selectBySimpleName(generator.getClass().getSimpleName());
                    tctx.loadTabBuffer(buf);
                    tctx.notify("Loaded: " + config.name);
                } catch (IOException e) {
                    tctx.registry().endBatch();
                    LOG.error("Failed to load preset '{}'", config.name, e);
                    tctx.notify("Load failed: " + config.name);
                }
            }
        });
        load.withUiHint(UiHint.ICON, "folder-open");
        addChild(load);

        AbstractAction delete = new AbstractAction("Delete", ctx -> {
            if (ctx instanceof TabActionContext tctx) {
                try {
                    tctx.tabStore().delete(config, generator);
                    onDeleted.run();
                    tctx.notify("Deleted: " + config.name);
                } catch (IOException e) {
                    LOG.error("Failed to delete preset '{}'", config.name, e);
                    tctx.notify("Delete failed: " + config.name);
                }
            }
        });
        delete.withUiHint(UiHint.ICON, "trash-2");
        addChild(delete);
    }
}
