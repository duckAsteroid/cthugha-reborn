package io.github.duckasteroid.cthugha.screenconfig;

import io.github.duckasteroid.cthugha.params.ContainerNode;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentStateStoreTest {

    @Test
    void firstLaunchHasNothingToRestore(@TempDir Path dir) throws IOException {
        CurrentStateStore store = new CurrentStateStore(dir);
        assertFalse(store.exists());

        ContainerNode root = new ContainerNode("Root");
        assertFalse(store.loadIfPresent(root), "no file yet -- nothing should be applied or thrown");
    }

    @Test
    void saveThenLoadRoundTripsLiveValues(@TempDir Path dir) throws IOException {
        ContainerNode root = new ContainerNode("Root");
        DoubleParameter amp = new DoubleParameter("amplitude", 0, 10, 3.5);
        root.addChild(amp);

        CurrentStateStore store = new CurrentStateStore(dir);
        store.save(root);
        assertTrue(store.exists());

        amp.setValue(0.0);
        assertTrue(store.loadIfPresent(root));
        assertEquals(3.5, amp.value);
    }

    @Test
    void fileIsReservedAndDotPrefixedSoItNeverAppearsInNamedConfigList(@TempDir Path dir) throws IOException {
        ContainerNode root = new ContainerNode("Root");
        CurrentStateStore currentStore = new CurrentStateStore(dir);
        currentStore.save(root);

        assertTrue(Files.exists(dir.resolve(".current.json")));

        ScreenConfigStore namedStore = new ScreenConfigStore(dir);
        List<ScreenConfig> named = namedStore.list();
        assertTrue(named.isEmpty(), "the reserved current-state file must not surface as a pickable named config");
    }
}
