package io.github.duckasteroid.cthugha.screenconfig;

import io.github.duckasteroid.cthugha.params.ContainerNode;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.StringParameter;
import io.github.duckasteroid.cthugha.tab.GeneratorRegistry;
import io.github.duckasteroid.cthugha.tab.TabGenerator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenConfigParamsTest {

    @Test
    void capturesAndRestoresLeafValues() {
        ContainerNode root = new ContainerNode("Root");
        DoubleParameter amp = new DoubleParameter("amplitude", 0, 10, 2.0);
        BooleanParameter enabled = new BooleanParameter("enabled", true);
        root.addChild(amp);
        root.addChild(enabled);

        Map<String, Object> snapshot = ScreenConfigParams.capture(root);

        amp.setValue(9.0);
        enabled.setValue(0);
        ScreenConfigParams.apply(root, snapshot);

        assertEquals(2.0, amp.value);
        assertTrue(enabled.value);
    }

    @Test
    void skipsPersistExcludedSubtrees() {
        ContainerNode root = new ContainerNode("Root");
        StringParameter transientField = new StringParameter("Save Name", "typed text");
        transientField.withNoPersist();
        DoubleParameter kept = new DoubleParameter("kept", 0, 10, 5.0);
        root.addChild(transientField);
        root.addChild(kept);

        Map<String, Object> snapshot = ScreenConfigParams.capture(root);

        assertFalse(snapshot.containsKey("Save Name"));
        assertTrue(snapshot.containsKey("kept"));
    }

    /**
     * {@link GeneratorRegistry} rebuilds its own children when its "Generator" selector param
     * changes (a different generator instance is swapped in as a child). This is the real-world
     * case that motivates per-entry re-resolution in {@link ScreenConfigParams#apply}: a naive
     * single recursive descent would iterate a children list that gets mutated out from under it
     * as soon as the selector's value is applied.
     */
    @Test
    void appliesThroughSelectorDrivenTreeRestructuring() {
        GeneratorRegistry registry = new GeneratorRegistry();
        TabGenerator first = registry.getSources().get(0);
        TabGenerator other = registry.getSources().get(registry.getSources().size() - 1);
        assertNotEquals(first.getClass(), other.getClass(), "test needs >1 registered generator");

        registry.selectBySimpleName(first.getClass().getSimpleName());
        Map<String, Object> snapshot = ScreenConfigParams.capture(registry);

        registry.selectBySimpleName(other.getClass().getSimpleName());
        assertEquals(other.getClass(), registry.getSelected().getClass());

        ScreenConfigParams.apply(registry, snapshot);

        assertEquals(first.getClass(), registry.getSelected().getClass());
    }
}
