package io.github.duckasteroid.cthugha.screenconfig;

import io.github.duckasteroid.cthugha.params.ContainerNode;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.StringParameter;
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

class ScreenConfigStoreTest {

    @Test
    void savesListsLoadsAndDeletesThroughRealJsonFiles(@TempDir Path dir) throws IOException {
        ContainerNode root = new ContainerNode("Root");
        DoubleParameter amp = new DoubleParameter("amplitude", 0, 10, 3.5);
        BooleanParameter enabled = new BooleanParameter("enabled", true);
        StringParameter label = new StringParameter("label", "hello");
        root.addChild(amp);
        root.addChild(enabled);
        root.addChild(label);

        ScreenConfigStore store = new ScreenConfigStore(dir);
        store.save("My Favourite!", root, false);

        List<ScreenConfig> saved = store.list();
        assertEquals(1, saved.size());
        assertEquals("My Favourite!", saved.get(0).name);

        // Mutate live values, then reload from the on-disk JSON round trip.
        amp.setValue(0.0);
        enabled.setValue(0);
        label.setValue("changed");

        store.load(saved.get(0), root);

        assertEquals(3.5, amp.value);
        assertTrue(enabled.value);
        assertEquals("hello", label.getValue());

        store.delete(saved.get(0));
        assertTrue(store.list().isEmpty());
    }

    @Test
    void savingOverAnExistingNameWithoutOverwriteThrowsAndLeavesFileUntouched(@TempDir Path dir) throws IOException {
        ContainerNode root = new ContainerNode("Root");
        DoubleParameter amp = new DoubleParameter("amplitude", 0, 10, 1.0);
        root.addChild(amp);

        ScreenConfigStore store = new ScreenConfigStore(dir);
        store.save("Chill Mode", root, false);
        assertTrue(store.exists("Chill Mode"));

        amp.setValue(9.0);
        assertThrows(ScreenConfigStore.ConfigAlreadyExistsException.class,
                () -> store.save("Chill Mode", root, false));

        // The on-disk file must still reflect the original save, not the mutated live value.
        List<ScreenConfig> saved = store.list();
        assertEquals(1, saved.size());
        assertEquals(1.0, ((Number) saved.get(0).params.get("amplitude")).doubleValue());
    }

    @Test
    void savingOverAnExistingNameWithOverwriteReplacesIt(@TempDir Path dir) throws IOException {
        ContainerNode root = new ContainerNode("Root");
        DoubleParameter amp = new DoubleParameter("amplitude", 0, 10, 1.0);
        root.addChild(amp);

        ScreenConfigStore store = new ScreenConfigStore(dir);
        store.save("Chill Mode", root, false);

        amp.setValue(9.0);
        store.save("Chill Mode", root, true);

        List<ScreenConfig> saved = store.list();
        assertEquals(1, saved.size());
        assertEquals(9.0, ((Number) saved.get(0).params.get("amplitude")).doubleValue());
    }

    @Test
    void listExcludesDotPrefixedReservedFiles(@TempDir Path dir) throws IOException {
        ContainerNode root = new ContainerNode("Root");
        ScreenConfigStore store = new ScreenConfigStore(dir);
        store.save("Visible Config", root, false);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(".current.json"), "{}");

        List<ScreenConfig> saved = store.list();
        assertEquals(1, saved.size());
        assertEquals("Visible Config", saved.get(0).name);
        assertFalse(saved.stream().anyMatch(c -> c.fileName != null && c.fileName.startsWith(".")));
    }
}
