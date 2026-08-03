package io.github.duckasteroid.cthugha.beatpresets;

import io.github.duckasteroid.cthugha.params.Node;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.action.AbstractAction;
import io.github.duckasteroid.cthugha.params.values.StringParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * "Presets" sub-node under the Beat Detector settings: lists saved beat presets and lets the
 * user capture the current band/tuning values under a name — e.g. "Dance", "Jazz", "Folk" —
 * to quickly re-tune the detector for different music. Mirrors {@link
 * io.github.duckasteroid.cthugha.screenconfig.ScreenConfigLibraryNode}, scoped to the Beat
 * Detector subtree instead of the whole tree.
 */
public class BeatPresetLibraryNode extends ParamNode {

    private static final Logger LOG = LoggerFactory.getLogger(BeatPresetLibraryNode.class);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final BeatPresetStore store;
    private final Node beatDetectorRoot;
    private final StringParameter saveName;
    private final AbstractAction saveAction;

    /**
     * Name the user has already been warned would overwrite an existing preset. Tapping Save
     * again with this exact (untouched) name confirms the overwrite; typing a different name, or
     * a successful save of any kind, clears it — see {@link #buildSaveAction()}.
     */
    private volatile String pendingOverwriteName;

    public BeatPresetLibraryNode(BeatPresetStore store, Node beatDetectorRoot) {
        super("Presets");
        withUiHint(UiHint.ICON, "bookmark");
        withNoPersist();
        withDescription("Named snapshots of this Beat Detector's band ranges and tuning; load "
            + "one to instantly re-tune for a different kind of music.");
        this.store = store;
        this.beatDetectorRoot = beatDetectorRoot;

        saveName = new StringParameter("Save Name", "");
        saveName.withNoPersist();
        saveName.withDescription("Name to save the current band/tuning values under. If left "
            + "blank, a timestamped name is generated.");
        saveAction = buildSaveAction();

        refresh();
    }

    /** Rebuilds the list of saved-preset children from disk. Call after any save or delete. */
    public void refresh() {
        List<Node> current = getChildren().collect(Collectors.toList());
        current.forEach(this::removeChild);
        for (BeatPreset preset : store.list()) {
            addChild(new BeatPresetNode(preset, store, beatDetectorRoot, this::refresh));
        }
        addChild(saveName);
        addChild(saveAction);
    }

    private AbstractAction buildSaveAction() {
        AbstractAction save = new AbstractAction("Save", ctx -> {
            String typed = saveName.getValue().trim();
            String name = typed.isEmpty()
                    ? "preset_" + LocalDateTime.now().format(TIMESTAMP)
                    : typed;
            boolean confirmedOverwrite = name.equals(pendingOverwriteName);
            try {
                store.save(name, beatDetectorRoot, confirmedOverwrite);
                pendingOverwriteName = null;
                saveName.setValue("");
                refresh();
                ctx.notify("saved: " + name);
            } catch (BeatPresetStore.PresetAlreadyExistsException e) {
                pendingOverwriteName = name;
                ctx.notify("'" + name + "' already exists — tap Save again to overwrite");
            } catch (IOException e) {
                LOG.error("Failed to save beat preset", e);
                ctx.notify("save failed");
            }
        });
        save.withUiHint(UiHint.ICON, "save");
        save.withDescription("Captures the current band ranges and threshold/sensitivity/decay/"
            + "history as a named, reloadable preset under Save Name (or an auto-generated name). "
            + "Saving over an existing name requires tapping Save twice to confirm.");
        return save;
    }
}
