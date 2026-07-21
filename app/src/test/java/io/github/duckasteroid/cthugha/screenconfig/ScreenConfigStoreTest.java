package io.github.duckasteroid.cthugha.screenconfig;

import io.github.duckasteroid.cthugha.params.ContainerNode;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.StringParameter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        store.save("My Favourite!", root);

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
}
