package io.github.duckasteroid.cthugha.display;

import io.github.duckasteroid.cthugha.config.Config;
import org.ini4j.Ini;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BeatTrackerSettingsNodeTest {

    private static Config configFrom(String ini) throws Exception {
        Ini parsed = new Ini();
        parsed.load(new StringReader(ini));
        return new Config(parsed);
    }

    @Test
    void defaultsToNoLatencyAndNormalTempo() throws Exception {
        BeatTrackerSettingsNode node = new BeatTrackerSettingsNode(configFrom(""));

        assertEquals(Duration.ZERO, node.latency());
        assertEquals(BeatClock.TempoScale.NORMAL, node.tempoScale());
    }

    @Test
    void readsBeatTrackerSection() throws Exception {
        BeatTrackerSettingsNode node = new BeatTrackerSettingsNode(
                configFrom("[BeatTracker]\nlatency_ms = 45\ntempo_scale = half\n"));

        assertEquals(Duration.ofMillis(45), node.latency());
        assertEquals(BeatClock.TempoScale.HALF, node.tempoScale());
    }

    @Test
    void clampsLatencyAndIgnoresUnknownScale() throws Exception {
        BeatTrackerSettingsNode node = new BeatTrackerSettingsNode(
                configFrom("[BeatTracker]\nlatency_ms = 5000\ntempo_scale = triple\n"));

        assertEquals(Duration.ofMillis(BeatTrackerSettingsNode.MAX_LATENCY_MS), node.latency());
        assertEquals(BeatClock.TempoScale.NORMAL, node.tempoScale());
    }

    @Test
    void notifiesOnChange() throws Exception {
        BeatTrackerSettingsNode node = new BeatTrackerSettingsNode(configFrom(""));
        int[] calls = {0};
        node.setOnChanged(() -> calls[0]++);

        node.latency.setValue(30);
        node.tempoScale.setEnumeration(BeatClock.TempoScale.DOUBLE);

        assertEquals(2, calls[0]);
        assertEquals(Duration.ofMillis(30), node.latency());
    }
}
