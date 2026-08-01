package io.github.duckasteroid.cthugha.screenconfig;

import io.github.duckasteroid.cthugha.params.ContainerNode;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link CurrentStatePersister}'s write-cadence logic directly via {@link
 * CurrentStatePersister#tick()} rather than waiting on real wall-clock scheduling — see the
 * class's own docs for why a plain debounce-after-last-change doesn't fit here (continuous
 * animation bindings mutate a value every tick, so this uses a fixed-interval "write only if
 * changed since last write" check instead).
 */
class CurrentStatePersisterTest {

    @Test
    void doesNotWriteWhenNothingChangedSinceLastTick(@TempDir Path dir) throws Exception {
        ContainerNode root = new ContainerNode("Root");
        DoubleParameter amp = new DoubleParameter("amplitude", 0, 10, 1.0);
        root.addChild(amp);
        CurrentStateStore store = new CurrentStateStore(dir);
        CurrentStatePersister persister = new CurrentStatePersister(store, root, Duration.ofSeconds(5));

        persister.tick();
        assertTrue(store.exists());
        Path file = dir.resolve(".current.json");
        FileTime firstWrite = Files.getLastModifiedTime(file);

        Thread.sleep(10); // ensure a distinguishable mtime if a (wrongful) second write occurs
        persister.tick();
        assertEquals(firstWrite, Files.getLastModifiedTime(file), "unchanged tree must not trigger a rewrite");
    }

    @Test
    void writesAgainOnceTheTreeChanges(@TempDir Path dir) throws Exception {
        ContainerNode root = new ContainerNode("Root");
        DoubleParameter amp = new DoubleParameter("amplitude", 0, 10, 1.0);
        root.addChild(amp);
        CurrentStateStore store = new CurrentStateStore(dir);
        CurrentStatePersister persister = new CurrentStatePersister(store, root, Duration.ofSeconds(5));

        persister.tick();
        amp.setValue(2.0);
        persister.tick();

        ContainerNode reloaded = new ContainerNode("Root");
        DoubleParameter reloadedAmp = new DoubleParameter("amplitude", 0, 10, 0.0);
        reloaded.addChild(reloadedAmp);
        store.loadIfPresent(reloaded);
        assertEquals(2.0, reloadedAmp.value, "the second write must reflect the changed value");
    }

    @Test
    void flushNowWritesEvenWithoutAPriorTick(@TempDir Path dir) {
        ContainerNode root = new ContainerNode("Root");
        DoubleParameter amp = new DoubleParameter("amplitude", 0, 10, 5.0);
        root.addChild(amp);
        CurrentStateStore store = new CurrentStateStore(dir);
        CurrentStatePersister persister = new CurrentStatePersister(store, root, Duration.ofSeconds(5));

        persister.flushNow();
        assertTrue(store.exists());
    }

    @Test
    void flushNowNeverThrowsEvenIfTheWriteFails(@TempDir Path dir) throws Exception {
        // A plain file where the store needs to create a directory forces Files.createDirectories
        // to throw (FileAlreadyExistsException) -- confirms flushNow() swallows I/O failures
        // rather than propagating them, since callers include shutdown paths.
        Path blocker = dir.resolve("blocker");
        Files.writeString(blocker, "not a directory");
        ContainerNode root = new ContainerNode("Root");
        CurrentStateStore store = new CurrentStateStore(blocker);
        CurrentStatePersister persister = new CurrentStatePersister(store, root, Duration.ofSeconds(5));

        assertFalse(store.exists());
        persister.flushNow(); // must not throw
    }
}
