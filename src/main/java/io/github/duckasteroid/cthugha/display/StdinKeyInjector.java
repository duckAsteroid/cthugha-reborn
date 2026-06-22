package io.github.duckasteroid.cthugha.display;

import com.asteroid.duck.opengl.util.keys.KeyCombination;
import com.asteroid.duck.opengl.util.keys.KeyRegistry;
import com.asteroid.duck.opengl.util.renderaction.RenderActionQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.Scanner;

/**
 * Reads single-character lines from stdin and dispatches them as key actions on the GL thread.
 * Only instantiated when --stdin is passed on the command line; when absent this class and its
 * thread do not exist at all.
 */
public class StdinKeyInjector implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(StdinKeyInjector.class);
    private static final String ACTION_TYPE = "stdin-key";

    private final RenderActionQueue renderActions;
    private Thread thread;

    StdinKeyInjector(RenderActionQueue renderActions) {
        this.renderActions = renderActions;
    }

    void start(KeyRegistry keyRegistry) {
        LOG.info("stdin key injection enabled — type a letter and press Enter");
        thread = Thread.ofVirtual()
                .name("stdin-key-injector")
                .start(() -> {
                    try (Scanner scanner = new Scanner(System.in)) {
                        while (scanner.hasNextLine() && !Thread.currentThread().isInterrupted()) {
                            String line = scanner.nextLine().trim();
                            if (!line.isEmpty()) {
                                char c = Character.toUpperCase(line.charAt(0));
                                if (Character.isAlphabetic(c)) {
                                    KeyCombination combo = KeyCombination.simple(c);
                                    renderActions.enqueue(ACTION_TYPE, ctx -> keyRegistry.handleCallback(combo));
                                }
                            }
                        }
                    }
                });
    }

    @Override
    public void close() {
        if (thread != null) {
            thread.interrupt();
        }
    }
}
