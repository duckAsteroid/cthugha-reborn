package io.github.duckasteroid.cthugha.screenconfig;

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
 * Root node for the "Configs" tab: lists saved screen configs and lets the user capture the
 * current whole-tree state (waves, animations, palette, active tab generator, blur, etc.) under
 * a name.
 *
 * <h2>Param tree layout</h2>
 * <pre>
 * Configs (this node, excluded from persistence itself)
 *   ├── &lt;saved config&gt;  [ScreenConfigNode — Load / Delete], one per saved config
 *   ├── Save Name  [StringParameter, excluded from persistence]
 *   └── Save       [Action]
 * </pre>
 */
public class ScreenConfigLibraryNode extends ParamNode {

    private static final Logger LOG = LoggerFactory.getLogger(ScreenConfigLibraryNode.class);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final ScreenConfigStore store;
    private final Node treeRoot;
    private final StringParameter saveName;
    private final AbstractAction saveAction;

    /**
     * Name the user has already been warned would overwrite an existing config. Tapping Save
     * again with this exact (untouched) name confirms the overwrite; typing a different name, or
     * a successful save of any kind, clears it — see {@link #buildSaveAction()}.
     */
    private volatile String pendingOverwriteName;

    public ScreenConfigLibraryNode(ScreenConfigStore store, Node treeRoot) {
        super("Configs");
        withUiHint(UiHint.ICON, "bookmark");
        withNoPersist();
        withDescription("Named snapshots of the whole parameter tree (waves, animations, "
            + "palette, active tab generator, blur, etc.); load one to instantly recall a "
            + "full look.");
        this.store = store;
        this.treeRoot = treeRoot;

        saveName = new StringParameter("Save Name", "");
        saveName.withNoPersist();
        saveName.withDescription("Name to save the current whole-tree state under. If left "
            + "blank, a timestamped name is generated.");
        saveAction = buildSaveAction();

        refresh();
    }

    /** Rebuilds the list of saved-config children from disk. Call after any save or delete. */
    public void refresh() {
        List<Node> current = getChildren().collect(Collectors.toList());
        current.forEach(this::removeChild);
        for (ScreenConfig config : store.list()) {
            addChild(new ScreenConfigNode(config, store, treeRoot, this::refresh));
        }
        addChild(saveName);
        addChild(saveAction);
    }

    private AbstractAction buildSaveAction() {
        AbstractAction save = new AbstractAction("Save", ctx -> {
            String typed = saveName.getValue().trim();
            String name = typed.isEmpty()
                    ? "config_" + LocalDateTime.now().format(TIMESTAMP)
                    : typed;
            // A second consecutive Save with the exact same (unchanged) name confirms an
            // overwrite the first attempt warned about below — no SPA changes needed for this
            // "tap twice to confirm" flow, since Save Name isn't cleared while a warning is
            // pending (see the catch block).
            boolean confirmedOverwrite = name.equals(pendingOverwriteName);
            try {
                store.save(name, treeRoot, confirmedOverwrite);
                pendingOverwriteName = null;
                saveName.setValue("");
                refresh();
                ctx.notify("saved: " + name);
            } catch (ScreenConfigStore.ConfigAlreadyExistsException e) {
                pendingOverwriteName = name;
                ctx.notify("'" + name + "' already exists — tap Save again to overwrite");
            } catch (IOException e) {
                LOG.error("Failed to save screen config", e);
                ctx.notify("save failed");
            }
        });
        save.withUiHint(UiHint.ICON, "save");
        save.withDescription("Captures the entire current parameter tree as a named, reloadable "
            + "snapshot under Save Name (or an auto-generated name). Saving over an existing "
            + "name requires tapping Save twice to confirm.");
        return save;
    }
}
