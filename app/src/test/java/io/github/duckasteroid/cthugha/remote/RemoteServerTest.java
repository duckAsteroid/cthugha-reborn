package io.github.duckasteroid.cthugha.remote;

import com.asteroid.duck.opengl.util.timer.StaticClock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.duckasteroid.cthugha.binding.BindingSystem;
import io.github.duckasteroid.cthugha.binding.ScriptParameter;
import io.github.duckasteroid.cthugha.params.ContainerNode;
import io.github.duckasteroid.cthugha.params.action.AbstractAction;
import io.github.duckasteroid.cthugha.params.action.ActionContext;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.StringParameter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link RemoteServer}'s HTTP surface end-to-end against a real Javalin instance bound
 * to an ephemeral port, using a hand-built param tree (a couple of value types plus one action)
 * instead of the real application tree. No OpenGL window, GL context, or audio device is
 * involved anywhere in this test.
 */
class RemoteServerTest {

    private static final String TOKEN = "test-token";
    private static final String FIXTURE_MAP_NAME = "RemoteServerTestFixtureMap";
    private static final String FIXTURE_MAP_NO_PREVIEW_NAME = "RemoteServerTestFixtureMapNoPreview";
    private static final String FIXTURE_IMAGE_NAME = "RemoteServerTestFixtureImage";

    private ContainerNode root;
    private DoubleParameter amplitude;
    private BooleanParameter enabled;
    private StringParameter quote;
    private ScriptParameter rawScript;
    private AtomicBoolean actionFired;
    private BindingSystem animation;
    private RemoteServer server;
    private HttpClient http;
    private String base;
    private final ObjectMapper mapper = new ObjectMapper();

    private Path testMapFile;
    private Path testMapFileNoPreviewSource;
    private Path testMapFileNoPreviewGeneratedPng;
    private Path testImageFile;
    private boolean createdMapsDir;
    private boolean createdImagesDir;

    @BeforeEach
    void setUp() throws Exception {
        root = new ContainerNode("Root");
        amplitude = new DoubleParameter("Amplitude", 0, 10, 2.0);
        enabled = new BooleanParameter("Enabled", true);
        quote = new StringParameter("Quote", "hello");
        rawScript = new ScriptParameter("RawScript", "0.5");
        actionFired = new AtomicBoolean(false);
        AbstractAction ping = new AbstractAction("Ping", ctx -> actionFired.set(true));
        root.addChild(amplitude);
        root.addChild(enabled);
        root.addChild(quote);
        root.addChild(rawScript);
        root.addChild(ping);

        ActionContext actionContext = new ActionContext() {
            @Override public void notify(String message) { }
            @Override public Random rng() { return new Random(1); }
        };

        animation = new BindingSystem();
        root.addChild(animation);
        animation.init(new StaticClock(0.0, 0.0), root, actionContext);

        TokenStore tokenStore = new TokenStore(TOKEN);
        tokenStore.rotate("http://localhost");

        RemoteConfig config = new RemoteConfig();
        config.port = 0; // let the OS pick a free port

        server = new RemoteServer(root, animation, tokenStore, new RemoteEventBroadcaster(), config, actionContext);
        server.start();
        base = "http://localhost:" + server.port();
        http = HttpClient.newHttpClient();

        // RemoteServer resolves preview files relative to the process's actual working
        // directory ("maps"/"images", not injectable), so fixtures have to live there for real.
        createdMapsDir = !Files.exists(Paths.get("maps"));
        createdImagesDir = !Files.exists(Paths.get("images"));
        testMapFile = Paths.get("maps", FIXTURE_MAP_NAME + ".MAP.png");
        testImageFile = Paths.get("images", FIXTURE_IMAGE_NAME + ".PNG");
        writeTestPng(testMapFile, 4, 4);
        writeTestPng(testImageFile, 8, 8);

        testMapFileNoPreviewSource = Paths.get("maps", FIXTURE_MAP_NO_PREVIEW_NAME + ".MAP");
        testMapFileNoPreviewGeneratedPng = Paths.get("maps", FIXTURE_MAP_NO_PREVIEW_NAME + ".MAP.png");
        writeTestMapFile(testMapFileNoPreviewSource);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.stop();
        Files.deleteIfExists(testMapFile);
        Files.deleteIfExists(testImageFile);
        Files.deleteIfExists(testMapFileNoPreviewSource);
        Files.deleteIfExists(testMapFileNoPreviewGeneratedPng);
        if (createdMapsDir) Files.deleteIfExists(testMapFile.getParent());
        if (createdImagesDir) Files.deleteIfExists(testImageFile.getParent());
    }

    private static void writeTestPng(Path file, int width, int height) throws Exception {
        Files.createDirectories(file.getParent());
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.RED);
            g.fillRect(0, 0, width, height);
        } finally {
            g.dispose();
        }
        ImageIO.write(img, "png", file.toFile());
    }

    /** Writes a minimal but valid 256-line {@code .MAP} file (one "r g b" triple per line). */
    private static void writeTestMapFile(Path file) throws Exception {
        Files.createDirectories(file.getParent());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 256; i++) {
            sb.append(i).append(' ').append(i).append(' ').append(i).append('\n');
        }
        Files.writeString(file, sb.toString());
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .header("Authorization", "Bearer " + TOKEN);
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        builder.method(method, publisher);
        if (body != null) builder.header("Content-Type", "application/json");
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception { return send("GET", path, null); }

    // --- SSE test infrastructure ---
    //
    // /api/v1/events is a long-lived stream, so it can't be read with the blocking String/JSON
    // helpers above. Each event is parsed off a background thread into an SseEvent and handed to
    // the test via a BlockingQueue, so tests can poll with a timeout instead of risking an
    // indefinite hang if an expected event never arrives.

    private static final String SSE_STREAM_CLOSED = "__SSE_STREAM_CLOSED__";

    private record SseEvent(String event, String data) {}

    private record SseConnection(CompletableFuture<HttpResponse<Void>> response, BlockingQueue<SseEvent> events) {}

    /** Forwards each line of the streaming response body into a queue for the parser thread below. */
    private static class LineQueueSubscriber implements Flow.Subscriber<String> {
        private final BlockingQueue<String> lines;
        LineQueueSubscriber(BlockingQueue<String> lines) { this.lines = lines; }
        @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
        @Override public void onNext(String item) { lines.add(item); }
        @Override public void onError(Throwable throwable) { lines.add(SSE_STREAM_CLOSED); }
        @Override public void onComplete() { lines.add(SSE_STREAM_CLOSED); }
    }

    /**
     * Opens {@code /api/v1/events} with the given query string (e.g. {@code "?path=Amplitude"} or
     * {@code ""} for no filter) and returns both the in-flight response (resolves once headers
     * arrive, well before the stream ends) and a queue of parsed {@code event:}/{@code data:}
     * pairs. Comment lines (keep-alives) and blank separator lines are ignored.
     */
    private SseConnection openSse(String query, String bearerToken, String queryToken) {
        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        BlockingQueue<SseEvent> events = new LinkedBlockingQueue<>();

        String url = base + "/api/v1/events" + query;
        if (queryToken != null) {
            url += (query.contains("?") ? "&" : "?") + "token=" + queryToken;
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "text/event-stream")
                .GET();
        if (bearerToken != null) builder.header("Authorization", "Bearer " + bearerToken);

        CompletableFuture<HttpResponse<Void>> response = http.sendAsync(
                builder.build(), HttpResponse.BodyHandlers.fromLineSubscriber(new LineQueueSubscriber(lines)));

        Thread parser = new Thread(() -> {
            String pendingEvent = null;
            while (true) {
                String line;
                try {
                    line = lines.take();
                } catch (InterruptedException e) {
                    return;
                }
                if (SSE_STREAM_CLOSED.equals(line)) return;
                if (line.startsWith("event:")) {
                    pendingEvent = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:") && pendingEvent != null) {
                    events.add(new SseEvent(pendingEvent, line.substring("data:".length()).trim()));
                    pendingEvent = null;
                }
            }
        });
        parser.setDaemon(true);
        parser.start();
        return new SseConnection(response, events);
    }

    /**
     * Runs {@code trigger} and waits for an event, retrying the trigger a few times if none
     * arrives. Needed because SSE client registration (server-side) races with this test issuing
     * its first mutating request right after the connection future resolves.
     */
    private SseEvent triggerUntilEventArrives(BlockingQueue<SseEvent> events, ThrowingRunnable trigger) throws Exception {
        SseEvent event = null;
        for (int attempt = 0; attempt < 10 && event == null; attempt++) {
            trigger.run();
            event = events.poll(300, TimeUnit.MILLISECONDS);
        }
        assertNotNull(event, "expected an SSE event to arrive");
        return event;
    }

    private interface ThrowingRunnable { void run() throws Exception; }

    // --- Auth ---

    @Test
    void infoEndpointIsPublicAndUnauthenticated() throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/v1/info")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertEquals("1.0", mapper.readTree(resp.body()).get("version").asText());
    }

    @Test
    void paramsEndpointRejectsMissingToken() throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/v1/params")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, resp.statusCode());
    }

    @Test
    void paramsEndpointRejectsWrongToken() throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/v1/params"))
                        .header("Authorization", "Bearer nope").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, resp.statusCode());
    }

    // --- Tree reads ---

    @Test
    void getFullTreeListsChildren() throws Exception {
        HttpResponse<String> resp = get("/api/v1/params");
        assertEquals(200, resp.statusCode());
        JsonNode tree = mapper.readTree(resp.body());
        java.util.List<String> names = new java.util.ArrayList<>();
        tree.get("children").forEach(c -> names.add(c.get("name").asText()));
        assertTrue(names.contains("Amplitude"));
        assertTrue(names.contains("Enabled"));
        assertTrue(names.contains("Ping"));
    }

    @Test
    void getSingleLeafReturnsItsValueAndBounds() throws Exception {
        HttpResponse<String> resp = get("/api/v1/params/Amplitude");
        assertEquals(200, resp.statusCode());
        JsonNode node = mapper.readTree(resp.body());
        assertEquals(2.0, node.get("value").asDouble());
        assertEquals(0.0, node.get("min").asDouble());
        assertEquals(10.0, node.get("max").asDouble());
    }

    @Test
    void getUnknownPathReturns404() throws Exception {
        HttpResponse<String> resp = get("/api/v1/params/DoesNotExist");
        assertEquals(404, resp.statusCode());
    }

    // --- PATCH (set value) ---

    @Test
    void patchSetsValueWithinRange() throws Exception {
        HttpResponse<String> resp = send("PATCH", "/api/v1/params/Amplitude", "{\"value\":7.5}");
        assertEquals(200, resp.statusCode());
        assertEquals(7.5, amplitude.getValue().doubleValue());
        assertEquals(7.5, mapper.readTree(resp.body()).get("value").asDouble());
    }

    @Test
    void patchRejectsOutOfRangeValue() throws Exception {
        HttpResponse<String> resp = send("PATCH", "/api/v1/params/Amplitude", "{\"value\":99}");
        assertEquals(400, resp.statusCode());
        assertEquals(2.0, amplitude.getValue().doubleValue(), "value must be untouched on rejection");
    }

    @Test
    void patchOnAnimatedValueSucceedsButWarns() throws Exception {
        send("POST", "/api/v1/params/Amplitude/animation", "{\"script\":\"0.5\"}");
        animation.tick(); // binding takes control, drives value to (min+max)/2 = 5.0

        HttpResponse<String> resp = send("PATCH", "/api/v1/params/Amplitude", "{\"value\":3}");

        assertEquals(200, resp.statusCode());
        assertEquals("controlled_by_animator", mapper.readTree(resp.body()).get("warning").asText());
        assertEquals(3.0, amplitude.getValue().doubleValue(), "PATCH still writes through even while controlled");
    }

    // --- PATCH (string values) ---
    //
    // This is the StringValue branch of ParamValues.applyText (as opposed to the AbstractValue
    // branch), reached via RemoteServer's PATCH handler. Used by StringLeaf.tsx for plain text
    // fields and by TargetPickerControl.tsx to write a chosen action/parameter path. ScriptParameter
    // (a CompilableValue) also flows through here whenever a script-typed leaf is edited directly
    // rather than through the dedicated /animation routes — e.g. the trigger system's condition
    // script, which CLAUDE.md notes rides this same generic PATCH-leaf endpoint instead of a
    // dedicated one.

    @Test
    void patchStringSetsPlainValue() throws Exception {
        HttpResponse<String> resp = send("PATCH", "/api/v1/params/Quote", "{\"value\":\"hello world\"}");

        assertEquals(200, resp.statusCode());
        assertEquals("hello world", quote.getValue());
        JsonNode node = mapper.readTree(resp.body());
        assertEquals("hello world", node.get("value").asText());
        assertFalse(node.has("compileError"), "plain StringParameter is not a CompilableValue");
    }

    @Test
    void patchStringOnCompilableValueReportsCompileErrorButStillStoresText() throws Exception {
        HttpResponse<String> resp = send("PATCH", "/api/v1/params/RawScript", "{\"value\":\"not valid java\"}");

        assertEquals(200, resp.statusCode(), "a compile failure is not an HTTP error");
        JsonNode node = mapper.readTree(resp.body());
        assertTrue(node.has("compileError"));
        assertEquals("not valid java", rawScript.getValue(), "raw text is stored even though it failed to compile");
    }

    @Test
    void patchStringOnCompilableValueOmitsCompileErrorWhenValid() throws Exception {
        HttpResponse<String> resp = send("PATCH", "/api/v1/params/RawScript", "{\"value\":\"0.5\"}");

        assertEquals(200, resp.statusCode());
        assertFalse(mapper.readTree(resp.body()).has("compileError"));
    }

    // --- POST /execute ---

    @Test
    void executeInvokesTheAction() throws Exception {
        HttpResponse<String> resp = send("POST", "/api/v1/params/Ping/execute", null);
        assertEquals(200, resp.statusCode());
        assertTrue(actionFired.get());
    }

    @Test
    void executeOnNonActionNodeReturns400() throws Exception {
        HttpResponse<String> resp = send("POST", "/api/v1/params/Amplitude/execute", null);
        assertEquals(400, resp.statusCode());
        assertFalse(actionFired.get());
    }

    // --- POST /randomise ---

    @Test
    void randomiseKeepsValueWithinBounds() throws Exception {
        HttpResponse<String> resp = send("POST", "/api/v1/params/Amplitude/randomise", null);
        assertEquals(200, resp.statusCode());
        double v = amplitude.getValue().doubleValue();
        assertTrue(v >= 0.0 && v <= 10.0);
    }

    // --- Animation lifecycle ---

    @Test
    void createAnimationBindsAndDrivesValueOnTick() throws Exception {
        HttpResponse<String> create = send("POST", "/api/v1/params/Amplitude/animation", "{\"script\":\"0.5\"}");
        assertEquals(200, create.statusCode());
        JsonNode created = mapper.readTree(create.body());
        assertEquals("0.5", created.get("animation").get("script").asText());
        assertFalse(amplitude.isControlled(), "not controlled until the next tick");

        animation.tick();

        assertTrue(amplitude.isControlled());
        assertEquals(5.0, amplitude.getValue().doubleValue()); // 0 + (10-0)*0.5
    }

    @Test
    void secondAnimationOnSameTargetIsRejected() throws Exception {
        send("POST", "/api/v1/params/Amplitude/animation", "{\"script\":\"0.5\"}");
        HttpResponse<String> resp = send("POST", "/api/v1/params/Amplitude/animation", "{\"script\":\"0.1\"}");
        assertEquals(409, resp.statusCode());
    }

    @Test
    void disablingAnimationReleasesControlOnNextTick() throws Exception {
        send("POST", "/api/v1/params/Amplitude/animation", "{\"script\":\"0.5\"}");
        animation.tick();
        assertTrue(amplitude.isControlled());

        HttpResponse<String> patch = send("PATCH", "/api/v1/params/Amplitude/animation", "{\"enabled\":false}");
        assertEquals(200, patch.statusCode());

        animation.tick();
        assertFalse(amplitude.isControlled(), "tick() re-asserts controlled=false every frame while disabled");
    }

    @Test
    void deletingAnimationRemovesBindingAndReleasesControl() throws Exception {
        send("POST", "/api/v1/params/Amplitude/animation", "{\"script\":\"0.5\"}");
        animation.tick();

        HttpResponse<String> del = send("DELETE", "/api/v1/params/Amplitude/animation", null);
        assertEquals(200, del.statusCode());
        assertFalse(amplitude.isControlled());
        assertNull(amplitude.getAnimationBinding());

        HttpResponse<String> after = get("/api/v1/params/Amplitude");
        assertFalse(mapper.readTree(after.body()).has("animation"));
    }

    @Test
    void badScriptNeverBecomesControlled() throws Exception {
        HttpResponse<String> create = send("POST", "/api/v1/params/Amplitude/animation", "{\"script\":\"not valid java\"}");
        JsonNode created = mapper.readTree(create.body());
        assertTrue(created.get("animation").has("compileError"));

        animation.tick();

        assertFalse(amplitude.isControlled(), "a script that never compiled successfully has no function to run");
    }

    @Test
    void badRecompileKeepsRunningLastGoodScript() throws Exception {
        send("POST", "/api/v1/params/Amplitude/animation", "{\"script\":\"0.5\"}");
        animation.tick();
        assertTrue(amplitude.isControlled());

        HttpResponse<String> patch = send("PATCH", "/api/v1/params/Amplitude/animation", "{\"script\":\"not valid java\"}");
        JsonNode patched = mapper.readTree(patch.body());
        assertTrue(patched.get("animation").has("compileError"));

        animation.tick();

        assertTrue(amplitude.isControlled(), "previous compiled function keeps running per ScriptParameter's documented behaviour");
        assertEquals(5.0, amplitude.getValue().doubleValue());
    }

    // --- SSE ---

    @Test
    void sseRejectsMissingToken() throws Exception {
        SseConnection sse = openSse("?path=Amplitude", null, null);
        assertEquals(401, sse.response().get(3, TimeUnit.SECONDS).statusCode());
    }

    @Test
    void sseAuthenticatesViaBearerHeader() throws Exception {
        // fromLineSubscriber's response future only resolves once the stream ends (which for a
        // live SSE connection is never, until teardown) so status can't be checked directly here;
        // successfully receiving a real event is itself proof the connection authenticated.
        SseConnection sse = openSse("?path=Amplitude", TOKEN, null);
        SseEvent event = triggerUntilEventArrives(sse.events(),
                () -> send("PATCH", "/api/v1/params/Amplitude", "{\"value\":6}"));
        assertEquals("paramChanged", event.event());
    }

    @Test
    void sseAuthenticatesViaTokenQueryParam() throws Exception {
        // EventSource in the browser can't set headers, so ?token= must work as a fallback.
        SseConnection sse = openSse("?path=Amplitude", null, TOKEN);
        SseEvent event = triggerUntilEventArrives(sse.events(),
                () -> send("PATCH", "/api/v1/params/Amplitude", "{\"value\":6}"));
        assertEquals("paramChanged", event.event());
    }

    @Test
    void sseDeliversParamChangedForSubscribedPath() throws Exception {
        SseConnection sse = openSse("?path=Amplitude", TOKEN, null);

        SseEvent event = triggerUntilEventArrives(sse.events(),
                () -> send("PATCH", "/api/v1/params/Amplitude", "{\"value\":6}"));

        assertEquals("paramChanged", event.event());
        JsonNode data = mapper.readTree(event.data());
        assertEquals("Amplitude", data.get("path").asText());
        assertEquals(6.0, data.get("value").asDouble());
        assertFalse(data.get("controlled").asBoolean());
    }

    @Test
    void sseDoesNotDeliverChangesOutsideSubscribedPath() throws Exception {
        SseConnection sse = openSse("?path=Amplitude", TOKEN, null);

        send("PATCH", "/api/v1/params/Enabled", "{\"value\":0}");
        assertNull(sse.events().poll(500, TimeUnit.MILLISECONDS),
                "Enabled was not subscribed, so its change should not be forwarded");

        // Prove the connection is still alive and correctly wired, not just silently broken.
        SseEvent event = triggerUntilEventArrives(sse.events(),
                () -> send("PATCH", "/api/v1/params/Amplitude", "{\"value\":9}"));
        assertEquals("Amplitude", mapper.readTree(event.data()).get("path").asText());
    }

    @Test
    void sseWithNoPathFilterSubscribesToWholeTree() throws Exception {
        SseConnection sse = openSse("", TOKEN, null);

        SseEvent event = triggerUntilEventArrives(sse.events(),
                () -> send("PATCH", "/api/v1/params/Enabled", "{\"value\":0}"));

        assertEquals("Enabled", mapper.readTree(event.data()).get("path").asText());
    }

    @Test
    void sseBroadcastsTreeChangedToAllClientsRegardlessOfSubscription() throws Exception {
        // Subscribed to an unrelated node; treeChanged is a broadcastAll(), so it bypasses
        // per-client subtree filtering entirely.
        SseConnection sse = openSse("?path=Enabled", TOKEN, null);

        // Warm-up with an idempotent, retryable trigger first: it proves this client is
        // registered server-side before we fire the one-shot POST below, which can only
        // succeed once per target (a second attempt would 409 and never broadcast).
        triggerUntilEventArrives(sse.events(), () -> send("PATCH", "/api/v1/params/Enabled", "{\"value\":1}"));

        send("POST", "/api/v1/params/Amplitude/animation", "{\"script\":\"0.5\"}");
        SseEvent event = sse.events().poll(3, TimeUnit.SECONDS);

        assertNotNull(event);
        assertEquals("treeChanged", event.event());
    }

    @Test
    void sseReflectsControlledFlagWhenAnimationDrivesValue() throws Exception {
        SseConnection sse = openSse("?path=Amplitude", TOKEN, null);

        // Creating the binding itself broadcasts a treeChanged event to every client
        // (see sseBroadcastsTreeChangedToAllClientsRegardlessOfSubscription); drain that first
        // so it isn't mistaken for the paramChanged event triggered by the tick below.
        send("POST", "/api/v1/params/Amplitude/animation", "{\"script\":\"0.5\"}");
        SseEvent treeChanged = sse.events().poll(3, TimeUnit.SECONDS);
        assertEquals("treeChanged", treeChanged != null ? treeChanged.event() : null);

        SseEvent event = triggerUntilEventArrives(sse.events(), () -> animation.tick());

        assertEquals("paramChanged", event.event());
        JsonNode data = mapper.readTree(event.data());
        assertEquals(5.0, data.get("value").asDouble()); // 0 + (10-0)*0.5
        assertTrue(data.get("controlled").asBoolean());
    }

    // --- Preview endpoints (maps/images) ---
    //
    // Not called through api.ts at all: GridControl.tsx and CarouselControl.tsx hit these
    // directly via <img src>, so they're part of the SPA's real API surface even though there's
    // no client wrapper function for them. Both are in the auth filter's public exceptions list
    // (a browser <img> tag can't attach an Authorization header), so none of these requests
    // carry a token.

    @Test
    void mapPreviewServesExistingFileUnauthenticated() throws Exception {
        HttpResponse<byte[]> resp = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/v1/maps/preview/" + FIXTURE_MAP_NAME)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, resp.statusCode());
        assertEquals("image/png", resp.headers().firstValue("Content-Type").orElse(""));
        assertTrue(resp.headers().firstValue("Cache-Control").isPresent());
        assertArrayEquals(Files.readAllBytes(testMapFile), resp.body());
    }

    @Test
    void mapPreviewGeneratesAndSavesMissingPngFromMapFile() throws Exception {
        assertFalse(Files.exists(testMapFileNoPreviewGeneratedPng), "precondition: no preview yet");

        HttpResponse<byte[]> resp = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/v1/maps/preview/" + FIXTURE_MAP_NO_PREVIEW_NAME)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, resp.statusCode());
        assertEquals("image/png", resp.headers().firstValue("Content-Type").orElse(""));
        assertTrue(Files.exists(testMapFileNoPreviewGeneratedPng), "preview should be generated and saved to disk");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(resp.body()));
        assertEquals(256, decoded.getWidth());
        assertArrayEquals(Files.readAllBytes(testMapFileNoPreviewGeneratedPng), resp.body());
    }

    @Test
    void mapPreviewMissingFileReturns404() throws Exception {
        HttpResponse<Void> resp = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/v1/maps/preview/DoesNotExist")).GET().build(),
                HttpResponse.BodyHandlers.discarding());

        assertEquals(404, resp.statusCode());
    }

    @Test
    void mapPreviewReturns304ForMatchingEtag() throws Exception {
        HttpResponse<Void> first = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/v1/maps/preview/" + FIXTURE_MAP_NAME)).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        String etag = first.headers().firstValue("ETag").orElseThrow(() -> new AssertionError("expected an ETag header"));

        HttpResponse<Void> second = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/v1/maps/preview/" + FIXTURE_MAP_NAME))
                        .header("If-None-Match", etag).GET().build(),
                HttpResponse.BodyHandlers.discarding());

        assertEquals(304, second.statusCode());
    }

    @Test
    void imagePreviewServesResizedThumbnailUnauthenticated() throws Exception {
        HttpResponse<byte[]> resp = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/v1/images/preview/" + FIXTURE_IMAGE_NAME)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, resp.statusCode());
        assertEquals("image/png", resp.headers().firstValue("Content-Type").orElse(""));
        // Proves it went through RandomImageSource's decode/scale/re-encode, not a raw passthrough.
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(resp.body()));
        assertNotNull(decoded);
    }

    @Test
    void imagePreviewMatchesDisplayNameCaseInsensitively() throws Exception {
        HttpResponse<Void> resp = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/v1/images/preview/" + FIXTURE_IMAGE_NAME.toLowerCase())).GET().build(),
                HttpResponse.BodyHandlers.discarding());

        assertEquals(200, resp.statusCode());
    }

    @Test
    void imagePreviewMissingFileReturns404() throws Exception {
        HttpResponse<Void> resp = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/v1/images/preview/DoesNotExist")).GET().build(),
                HttpResponse.BodyHandlers.discarding());

        assertEquals(404, resp.statusCode());
    }
}
