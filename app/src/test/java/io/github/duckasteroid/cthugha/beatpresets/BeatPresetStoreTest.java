package io.github.duckasteroid.cthugha.beatpresets;

import io.github.duckasteroid.cthugha.params.ContainerNode;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeatPresetStoreTest {

    @Test
    void savesListsLoadsAndDeletesThroughRealJsonFiles(@TempDir Path dir) throws IOException {
        ContainerNode root = new ContainerNode("Beat Detector");
        DoubleParameter threshold = new DoubleParameter("Threshold", 1.0, 5.0, 1.3);
        IntegerParameter history = new IntegerParameter("History", 1, 300, 43);
        root.addChild(threshold);
        root.addChild(history);

        BeatPresetStore store = new BeatPresetStore(dir);
        store.save("Dance", root, false);

        List<BeatPreset> saved = store.list();
        assertEquals(1, saved.size());
        assertEquals("Dance", saved.get(0).name);

        threshold.setValue(2.5);
        history.setValue(100);

        store.load(saved.get(0), root);

        assertEquals(1.3, threshold.value);
        assertEquals(43, history.value);

        store.delete(saved.get(0));
        assertTrue(store.list().isEmpty());
    }

    @Test
    void savingOverAnExistingNameWithoutOverwriteThrowsAndLeavesFileUntouched(@TempDir Path dir) throws IOException {
        ContainerNode root = new ContainerNode("Beat Detector");
        DoubleParameter threshold = new DoubleParameter("Threshold", 1.0, 5.0, 1.3);
        root.addChild(threshold);

        BeatPresetStore store = new BeatPresetStore(dir);
        store.save("Jazz", root, false);
        assertTrue(store.exists("Jazz"));

        threshold.setValue(4.0);
        assertThrows(BeatPresetStore.PresetAlreadyExistsException.class,
                () -> store.save("Jazz", root, false));

        List<BeatPreset> saved = store.list();
        assertEquals(1, saved.size());
        assertEquals(1.3, ((Number) saved.get(0).params.get("Threshold")).doubleValue());
    }

    @Test
    void savingOverAnExistingNameWithOverwriteReplacesIt(@TempDir Path dir) throws IOException {
        ContainerNode root = new ContainerNode("Beat Detector");
        DoubleParameter threshold = new DoubleParameter("Threshold", 1.0, 5.0, 1.3);
        root.addChild(threshold);

        BeatPresetStore store = new BeatPresetStore(dir);
        store.save("Folk", root, false);

        threshold.setValue(4.0);
        store.save("Folk", root, true);

        List<BeatPreset> saved = store.list();
        assertEquals(1, saved.size());
        assertEquals(4.0, ((Number) saved.get(0).params.get("Threshold")).doubleValue());
    }

    @Test
    void listExcludesDotPrefixedReservedFiles(@TempDir Path dir) throws IOException {
        ContainerNode root = new ContainerNode("Beat Detector");
        BeatPresetStore store = new BeatPresetStore(dir);
        store.save("Visible Preset", root, false);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(".hidden.json"), "{}");

        List<BeatPreset> saved = store.list();
        assertEquals(1, saved.size());
        assertEquals("Visible Preset", saved.get(0).name);
        assertFalse(saved.stream().anyMatch(p -> p.fileName != null && p.fileName.startsWith(".")));
    }
}
