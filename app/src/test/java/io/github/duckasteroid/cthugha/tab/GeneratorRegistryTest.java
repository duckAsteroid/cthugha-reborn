package io.github.duckasteroid.cthugha.tab;

import io.github.duckasteroid.cthugha.params.AbstractValue;
import io.github.duckasteroid.cthugha.params.Node;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratorRegistryTest {

    @Test
    void noGeneratorValueIsAnimatable() {
        GeneratorRegistry registry = new GeneratorRegistry();
        List<AbstractValue> values = new ArrayList<>();
        registry.getSources().forEach(g -> collect(g, values));

        assertFalse(values.isEmpty());
        for (AbstractValue v : values) {
            assertFalse(v.isAnimatable(), v.getName() + " should not be animatable");
        }
    }

    @Test
    void requestRandomiseFiresTheNewGeneratorCallback() {
        GeneratorRegistry registry = new GeneratorRegistry();
        boolean[] fired = {false};
        registry.setOnNewGeneratorSelected(() -> fired[0] = true);

        registry.requestRandomise();

        assertTrue(fired[0]);
    }

    private static void collect(Node node, List<AbstractValue> out) {
        if (node instanceof AbstractValue av) {
            out.add(av);
        } else {
            node.getChildren().forEach(c -> collect(c, out));
        }
    }
}
