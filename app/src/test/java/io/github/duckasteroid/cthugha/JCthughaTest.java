package io.github.duckasteroid.cthugha;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JCthughaTest {

    @Test
    void notifyRoundTrip() {
        JCthugha app = new JCthugha();
        assertNull(app.pollNotification());
        app.notify("hello");
        assertEquals("hello", app.pollNotification());
        assertNull(app.pollNotification());
    }

    @Test
    void notifyLastMessageWins() {
        JCthugha app = new JCthugha();
        app.notify("first");
        app.notify("second");
        assertEquals("second", app.pollNotification());
        assertNull(app.pollNotification());
    }

    @Test
    void toggleNotificationsSuppresses() {
        JCthugha app = new JCthugha();
        app.toggleNotifications();
        app.notify("suppressed");
        assertNull(app.pollNotification());
        // re-enable
        app.toggleNotifications();
        app.notify("visible");
        assertEquals("visible", app.pollNotification());
    }

    @Test
    void getCurrentQuoteNullBeforeShowQuote() {
        JCthugha app = new JCthugha();
        assertNull(app.getCurrentQuote());
    }

    @Test
    void showQuoteMakesQuoteAvailable() {
        JCthugha app = new JCthugha();
        app.showQuote();
        var quote = app.getCurrentQuote();
        assertNotNull(quote);
        assertNotNull(quote.author(), "quote should have an author");
    }

    @Test
    void showQuoteReplacesExistingQuote() {
        JCthugha app = new JCthugha();
        app.showQuote();
        var first = app.getCurrentQuote();
        // Call again; may get same text but should not throw and should be non-null
        app.showQuote();
        assertNotNull(app.getCurrentQuote());
    }
}
