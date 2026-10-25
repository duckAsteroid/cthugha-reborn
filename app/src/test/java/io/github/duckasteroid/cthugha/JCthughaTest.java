package io.github.duckasteroid.cthugha;

import io.github.duckasteroid.cthugha.tab.TabBuffer;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.util.Random;

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
        app.notifications.setValue(0);
        app.notify("suppressed");
        assertNull(app.pollNotification());
        // re-enable
        app.notifications.setValue(1);
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

    private static JCthugha withSmallTable() {
        JCthugha app = new JCthugha();
        app.bufferWidth = 8;
        app.bufferHeight = 4;
        app.rng = new Random(1);
        app.translate = new TabBuffer(new Dimension(8, 4));
        return app;
    }

    @Test
    void adoptedTranslationsReuseTheReplacedBuffer() {
        JCthugha app = withSmallTable();
        TabBuffer original = app.getTabBuffer();

        JCthugha.ComputedTranslation first = app.computeTranslation(false);
        assertTrue(app.adoptTranslation(first));
        assertSame(first.buffer(), app.getTabBuffer());

        JCthugha.ComputedTranslation second = app.computeTranslation(false);
        assertSame(original, second.buffer(), "the buffer replaced by the last adoption is reused");
        assertTrue(app.adoptTranslation(second));
        assertSame(first.buffer(), app.computeTranslation(false).buffer());
    }

    @Test
    void translationComputedBeforeAPresetLoadIsDiscarded() {
        JCthugha app = withSmallTable();
        JCthugha.ComputedTranslation stale = app.computeTranslation(false);
        TabBuffer preset = new TabBuffer(new Dimension(8, 4));
        app.loadTabBuffer(preset);

        assertFalse(app.adoptTranslation(stale));
        assertSame(preset, app.getTabBuffer());
    }
}
