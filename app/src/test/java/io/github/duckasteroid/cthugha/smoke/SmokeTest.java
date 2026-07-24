package io.github.duckasteroid.cthugha.smoke;

import org.junit.jupiter.api.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end smoke test: launches the installed distribution, drives it through a named
 * pipe ({@code --key-input}), and checks it doesn't crash, produces non-blank screenshots,
 * and serves a healthy remote API.
 *
 * A named pipe is used instead of the child process's own stdin because the installed
 * launch script doesn't {@code exec} into java (it runs java as its own child so it can
 * restore the audio source afterwards) - so the `Process` handed back by ProcessBuilder is
 * the shell wrapper, not the JVM, and writing to its stdin pipe never reaches the app.
 * A FIFO is opened directly by the app via a real filesystem path, independent of that
 * process tree.
 *
 * Requires a real display and spawns a GLFW window, so it is excluded from the regular
 * `test` task and only runs via `./gradlew smokeTest` (see app/build.gradle).
 */
@Tag("smoke")
class SmokeTest {

    private static final int REMOTE_PORT = 18363;
    private static final String REMOTE_TOKEN = "smoke-test-token";

    @Test
    @Timeout(60)
    void appLaunchesRendersAndServesRemoteApi() throws Exception {
        String binaryPath = System.getProperty("smoketest.binary");
        Assumptions.assumeTrue(binaryPath != null, "smoketest.binary not set - run via `./gradlew smokeTest`");

        File binary = new File(binaryPath);
        assertTrue(binary.exists(), "installed binary not found: " + binary);
        File workDir = new File(System.getProperty("smoketest.workdir"));
        File reportDir = new File(System.getProperty("smoketest.reportdir"));
        reportDir.mkdirs();

        Set<String> before = screenshotNames(workDir);

        Path fifoDir = Files.createTempDirectory("cthugha-smoketest");
        Path fifo = fifoDir.resolve("keys.fifo");
        assertTrue(new ProcessBuilder("mkfifo", fifo.toString()).start().waitFor(5, TimeUnit.SECONDS),
                "mkfifo timed out");

        Process process = new ProcessBuilder(
                binary.getAbsolutePath(),
                "--key-input=" + fifo,
                "--remote-port=" + REMOTE_PORT, "--remote-token=" + REMOTE_TOKEN)
                .directory(workDir)
                .redirectErrorStream(true)
                .start();

        List<String> logLines = Collections.synchronizedList(new ArrayList<>());
        Thread reader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    logLines.add(line);
                }
            } catch (IOException ignored) {
            }
        }, "smoke-test-log-reader");
        reader.setDaemon(true);
        reader.start();

        try {
            // Blocks until the app's key injector opens the FIFO for reading (which happens
            // right after init(), so the app is already fully set up by the time this returns).
            PrintWriter stdin = new PrintWriter(new FileOutputStream(fifo.toString()), true);
            Thread.sleep(1000);

            // Hitting any authenticated endpoint fires onFirstAuth, which hides the startup QR
            // overlay - do this, and force notifications off, before any screenshot is taken so
            // neither obscures the render buffer. Notifications default true and persist across
            // runs in state.ini, so this must set an absolute value rather than toggle via "N".
            ApiResult api = disableNotifications();
            Thread.sleep(500);

            press(stdin, "PRINT_SCREEN", 500);   // baseline
            press(stdin, "P", 500);     // change palette
            press(stdin, "PRINT_SCREEN", 500);
            press(stdin, "T", 500);     // randomise translation
            press(stdin, "PRINT_SCREEN", 500);
            press(stdin, "I", 800);     // flash a random image
            press(stdin, "PRINT_SCREEN", 500);
            press(stdin, "Q", 500);     // show a quote
            press(stdin, "PRINT_SCREEN", 500);
            press(stdin, "X", 500);     // flash white
            press(stdin, "PRINT_SCREEN", 500);

            stdin.println("ESCAPE"); // quit
            stdin.flush();
            stdin.close();

            boolean exited = process.waitFor(10, TimeUnit.SECONDS);
            reader.join(2000);

            List<File> screenshots = archiveNewScreenshots(workDir, before, reportDir);
            Files.write(new File(reportDir, "output.log").toPath(), logLines);

            List<String> failures = new ArrayList<>();
            if (!exited) {
                failures.add("process did not exit within 10s of SHIFT+1");
            } else if (process.exitValue() != 0) {
                failures.add("process exited with code " + process.exitValue());
            }
            logLines.stream()
                    .filter(SmokeTest::looksLikeAnError)
                    .findFirst()
                    .ifPresent(l -> failures.add("log contains an error: " + l));
            if (screenshots.size() != 6) {
                failures.add("expected 6 screenshots, found " + screenshots.size());
            }
            for (File f : screenshots) {
                failures.addAll(checkScreenshot(f));
            }
            if (!api.ok()) {
                failures.add("remote API check failed: " + api.error());
            }

            assertTrue(failures.isEmpty(), "Smoke test failures (see " + reportDir + "):\n - "
                    + String.join("\n - ", failures));
        } finally {
            // The installed launch script runs java as its own child (see class javadoc), so
            // killing `process` alone leaves it running - destroy the whole descendant tree.
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            deleteRecursively(fifoDir);
        }
    }

    private static void press(PrintWriter stdin, String key, long delayMs) throws InterruptedException {
        stdin.println(key);
        stdin.flush();
        Thread.sleep(delayMs);
    }

    /** Matches the logback console pattern `%d{HH:mm:ss} %-5level ...` (WARN+ only) or a raw uncaught exception. */
    private static boolean looksLikeAnError(String line) {
        if (line.contains("Exception in thread")) {
            return true;
        }
        String[] parts = line.trim().split("\\s+", 3);
        return parts.length > 1 && parts[1].equals("ERROR");
    }

    private static Set<String> screenshotNames(File dir) {
        String[] names = dir.list((d, n) -> n.startsWith("screenshot-") && n.endsWith(".png"));
        return names == null ? Set.of() : new HashSet<>(Arrays.asList(names));
    }

    private static List<File> archiveNewScreenshots(File workDir, Set<String> before, File reportDir) throws IOException {
        String[] names = workDir.list((d, n) -> n.startsWith("screenshot-") && n.endsWith(".png"));
        List<String> newNames = names == null ? List.of()
                : Arrays.stream(names).filter(n -> !before.contains(n)).sorted().collect(Collectors.toList());
        List<File> archived = new ArrayList<>();
        for (String name : newNames) {
            File dest = new File(reportDir, name);
            Files.move(new File(workDir, name).toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            archived.add(dest);
        }
        return archived;
    }

    private static void deleteRecursively(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static List<String> checkScreenshot(File f) {
        List<String> problems = new ArrayList<>();
        if (f.length() < 2048) {
            problems.add(f.getName() + " is suspiciously small (" + f.length() + " bytes)");
            return problems;
        }
        BufferedImage img;
        try {
            img = ImageIO.read(f);
        } catch (IOException e) {
            problems.add(f.getName() + " could not be read: " + e.getMessage());
            return problems;
        }
        if (img == null) {
            problems.add(f.getName() + " is not a decodable PNG");
        } else if (isBlank(img)) {
            problems.add(f.getName() + " appears blank (uniform colour)");
        }
        return problems;
    }

    private static boolean isBlank(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int first = img.getRGB(0, 0);
        int stepX = Math.max(1, w / 20);
        int stepY = Math.max(1, h / 20);
        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                if (img.getRGB(x, y) != first) {
                    return false;
                }
            }
        }
        return true;
    }

    private record ApiResult(boolean ok, String error) {}

    /**
     * PATCHes Notifications to an absolute false (it defaults true and persists across runs in
     * state.ini, so toggling via the "N" key would be order-dependent) and doubles as the remote
     * API health check. Also incidentally hides the startup QR overlay, since any authenticated
     * request fires RemoteServer's onFirstAuth callback.
     */
    private static ApiResult disableNotifications() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + REMOTE_PORT + "/api/v1/params/General/Notifications"))
                    .header("Authorization", "Bearer " + REMOTE_TOKEN)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(3))
                    .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"value\":\"0\"}"))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return new ApiResult(false, "HTTP " + response.statusCode() + ": " + response.body());
            }
            String body = response.body().trim();
            if (!body.startsWith("{") && !body.startsWith("[")) {
                return new ApiResult(false, "response body doesn't look like JSON: " + body);
            }
            return new ApiResult(true, null);
        } catch (Exception e) {
            return new ApiResult(false, e.toString());
        }
    }
}
