package io.github.duckasteroid.cthugha.beatpresets;

import io.github.duckasteroid.cthugha.params.Node;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.action.AbstractAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * A container node representing one saved beat preset.
 *
 * <p>Named after the preset's display name, with two {@link AbstractAction} children:</p>
 * <ul>
 *   <li><b>Load</b> — applies the preset's captured band/tuning values to the Beat Detector subtree.</li>
 *   <li><b>Delete</b> — removes the preset from disk and refreshes the parent {@link BeatPresetLibraryNode}.</li>
 * </ul>
 */
public class BeatPresetNode extends ParamNode {

    private static final Logger LOG = LoggerFactory.getLogger(BeatPresetNode.class);

    public BeatPresetNode(BeatPreset preset, BeatPresetStore store, Node beatDetectorRoot, Runnable onDeleted) {
        super(preset.name);

        AbstractAction load = new AbstractAction("Load", ctx -> {
            store.load(preset, beatDetectorRoot);
            ctx.notify("Loaded: " + preset.name);
        });
        load.withUiHint(UiHint.ICON, "folder-open");
        load.withDescription("Restores this saved preset's band ranges and threshold/sensitivity/"
            + "decay/history onto the live beat detector.");
        addChild(load);

        AbstractAction delete = new AbstractAction("Delete", ctx -> {
            try {
                store.delete(preset);
                onDeleted.run();
                ctx.notify("Deleted: " + preset.name);
            } catch (IOException e) {
                LOG.error("Failed to delete beat preset '{}'", preset.name, e);
                ctx.notify("Delete failed: " + preset.name);
            }
        });
        delete.withUiHint(UiHint.ICON, "trash-2");
        delete.withDescription("Permanently removes this saved beat preset from disk.");
        addChild(delete);
    }
}
