package io.github.duckasteroid.cthugha.tab;

import io.github.duckasteroid.cthugha.tab.TranslationRegenerator.Request;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationRegeneratorTest {

    /** Records runs; each run blocks until released, to simulate a regeneration in flight. */
    private static final class BlockingJob {
        final List<Request> runs = new CopyOnWriteArrayList<>();
        final CountDownLatch firstStarted = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch done;

        BlockingJob(int expectedRuns) {
            done = new CountDownLatch(expectedRuns);
        }

        void run(Request r) {
            runs.add(r);
            firstStarted.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            done.countDown();
        }
    }

    private static TranslationRegenerator regenerator(BlockingJob job, List<Thread> threads) {
        return new TranslationRegenerator(drain -> {
            Thread t = Thread.ofVirtual().start(drain);
            threads.add(t);
        }, job::run);
    }

    @Test
    void requestsDuringARunCoalesceIntoOneFollowUpRun() throws Exception {
        BlockingJob job = new BlockingJob(2);
        List<Thread> threads = new CopyOnWriteArrayList<>();
        TranslationRegenerator regen = regenerator(job, threads);

        regen.request(Request.REGENERATE);
        assertTrue(job.firstStarted.await(5, TimeUnit.SECONDS));
        for (int i = 0; i < 20; i++) regen.request(Request.REGENERATE);
        job.release.countDown();

        assertTrue(job.done.await(5, TimeUnit.SECONDS));
        for (Thread t : threads) t.join(5000);
        assertEquals(List.of(Request.REGENERATE, Request.REGENERATE), job.runs);
        assertEquals(1, threads.size(), "only one drain loop should ever be started at a time");
    }

    @Test
    void pendingRandomiseIsNotDowngradedByALaterRegenerate() throws Exception {
        BlockingJob job = new BlockingJob(2);
        List<Thread> threads = new CopyOnWriteArrayList<>();
        TranslationRegenerator regen = regenerator(job, threads);

        regen.request(Request.REGENERATE);
        assertTrue(job.firstStarted.await(5, TimeUnit.SECONDS));
        regen.request(Request.RANDOMISE);
        regen.request(Request.REGENERATE);
        job.release.countDown();

        assertTrue(job.done.await(5, TimeUnit.SECONDS));
        for (Thread t : threads) t.join(5000);
        assertEquals(List.of(Request.REGENERATE, Request.RANDOMISE), job.runs);
    }

    @Test
    void aRequestAfterTheDrainFinishesStartsANewRun() throws Exception {
        BlockingJob job = new BlockingJob(2);
        job.release.countDown();
        List<Thread> threads = new CopyOnWriteArrayList<>();
        TranslationRegenerator regen = regenerator(job, threads);

        regen.request(Request.REGENERATE);
        threads.get(0).join(5000);
        regen.request(Request.RANDOMISE);

        assertTrue(job.done.await(5, TimeUnit.SECONDS));
        assertEquals(List.of(Request.REGENERATE, Request.RANDOMISE), job.runs);
        assertEquals(2, threads.size());
    }
}
