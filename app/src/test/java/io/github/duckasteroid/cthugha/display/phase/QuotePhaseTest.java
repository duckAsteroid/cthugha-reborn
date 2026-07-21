package io.github.duckasteroid.cthugha.display.phase;

import com.asteroid.duck.opengl.util.renderaction.RenderActionQueue;
import io.github.duckasteroid.cthugha.JCthugha;
import io.github.duckasteroid.cthugha.params.ContainerNode;
import io.github.duckasteroid.cthugha.params.action.Action;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless tests for {@link QuotePhase} — no GL context required.
 * Verifies param-tree action registration and the quoteInBuffer toggle.
 */
class QuotePhaseTest {

    private JCthugha app;
    private QuotePhase phase;
    private ContainerNode group;

    @BeforeEach
    void setUp() {
        app = new JCthugha();
        phase = new QuotePhase(app);
        group = new ContainerNode("General");
        phase.registerActions(group, new RenderActionQueue());
    }

    @Test
    void registerActionsAddsTwoEntries() {
        long count = group.getChildren().count();
        assertEquals(2, count, "Toggle Quote Mode, Transform");
    }

    @Test
    void toggleQuoteModeActionNamedCorrectly() {
        assertTrue(group.getChild("Toggle Quote Mode").isPresent());
    }

    @Test
    void transformNodeRegistered() {
        assertTrue(group.getChild("Transform").isPresent());
        assertSame(phase.transform, group.getChild("Transform").orElseThrow());
    }

    @Test
    void toggleQuoteModeFlipsNotificationToInBuffer() {
        Action toggle = (Action) group.getChild("Toggle Quote Mode").orElseThrow();
        toggle.execute(null);  // action body does not use ctx
        assertEquals("quote: in buffer", app.pollNotification());
    }

    @Test
    void toggleQuoteModeCyclesBackToOverlayAfterThreeToggles() {
        Action toggle = (Action) group.getChild("Toggle Quote Mode").orElseThrow();
        toggle.execute(null);
        app.pollNotification();  // consume "in buffer"
        toggle.execute(null);
        assertEquals("quote: both", app.pollNotification());
        toggle.execute(null);
        assertEquals("quote: overlay", app.pollNotification());
    }
}
