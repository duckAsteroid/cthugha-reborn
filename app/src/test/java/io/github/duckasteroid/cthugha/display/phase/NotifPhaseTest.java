package io.github.duckasteroid.cthugha.display.phase;

import com.asteroid.duck.opengl.util.renderaction.RenderActionQueue;
import io.github.duckasteroid.cthugha.JCthugha;
import io.github.duckasteroid.cthugha.params.ContainerNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Headless tests for {@link NotifPhase} — no GL context required.
 * Notifications are produced by {@link JCthugha#notify(String)} and polled by the phase;
 * the poll/produce contract is verified here.
 */
class NotifPhaseTest {

    @Test
    void registerActionsAddsNoEntries() {
        JCthugha app = new JCthugha();
        ContainerNode group = new ContainerNode("General");
        new NotifPhase(app).registerActions(group, new RenderActionQueue());
        assertEquals(0, group.getChildren().count(),
                "NotifPhase should not register any param-tree actions");
    }

    @Test
    void notificationProduceConsumeRoundTrip() {
        // Verifies the JCthugha.notify / pollNotification contract that NotifPhase relies on.
        JCthugha app = new JCthugha();
        app.notify("hello");
        assertEquals("hello", app.pollNotification());
    }

    @Test
    void lastNotificationWins() {
        JCthugha app = new JCthugha();
        app.notify("first");
        app.notify("second");
        assertEquals("second", app.pollNotification());
    }
}
