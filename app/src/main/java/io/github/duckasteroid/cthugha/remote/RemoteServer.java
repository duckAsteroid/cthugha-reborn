package io.github.duckasteroid.cthugha.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpResponseException;
import io.javalin.http.staticfiles.Location;
import io.javalin.http.sse.SseClient;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import io.github.duckasteroid.cthugha.binding.BindingSystem;
import io.github.duckasteroid.cthugha.binding.ContinuousBinding;
import io.github.duckasteroid.cthugha.binding.EdgeTriggeredBinding;
import io.github.duckasteroid.cthugha.img.ImageEntry;
import io.github.duckasteroid.cthugha.img.ImageLibrary;
import io.github.duckasteroid.cthugha.img.RandomImageSource;
import io.github.duckasteroid.cthugha.map.MapFileReader;
import io.github.duckasteroid.cthugha.video.VideoEntry;
import io.github.duckasteroid.cthugha.video.VideoLibrary;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.AbstractValue;
import io.github.duckasteroid.cthugha.params.action.Action;
import io.github.duckasteroid.cthugha.params.action.ActionContext;
import io.github.duckasteroid.cthugha.params.CompilableValue;
import io.github.duckasteroid.cthugha.params.Node;
import io.github.duckasteroid.cthugha.params.ParamValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class RemoteServer {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteServer.class);
    private static final String API_PARAMS_PREFIX = "/api/v1/params/";

    private final Node paramRoot;
    private final BindingSystem bindings;
    private final TokenStore tokenStore;
    private final RemoteEventBroadcaster broadcaster;
    private final RemoteConfig config;
    private final ActionContext actionContext;
    private final ParamSerializer serializer;
    private final ObjectMapper mapper;

    private Javalin app;
    private volatile Runnable onFirstAuth;
    private final AtomicBoolean firstAuthFired = new AtomicBoolean(false);
    private final RandomImageSource imageSource = new RandomImageSource(Paths.get("images"));
    private final ImageLibrary imageLibrary = new ImageLibrary(Paths.get("images"), imageSource);
    private final MapFileReader mapReader = new MapFileReader(Paths.get("maps"));
    private final VideoLibrary videoLibrary = new VideoLibrary(Paths.get("videos"));
    private static final int THUMBNAIL_MAX_DIM = 240;

    public RemoteServer(Node paramRoot, BindingSystem bindings, TokenStore tokenStore,
                        RemoteEventBroadcaster broadcaster, RemoteConfig config,
                        ActionContext actionContext) {
        this.paramRoot = paramRoot;
        this.bindings = bindings;
        this.tokenStore = tokenStore;
        this.broadcaster = broadcaster;
        this.config = config;
        this.actionContext = actionContext;
        this.serializer = new ParamSerializer(bindings);
        this.mapper = serializer.getMapper();
    }

    public void setOnFirstAuth(Runnable r) {
        this.onFirstAuth = r;
    }

    public void start() {
        app = Javalin.create(cfg -> {
            cfg.staticFiles.add("/remote", Location.CLASSPATH);
            cfg.spaRoot.addFile("/", "/remote/index.html", Location.CLASSPATH);
            cfg.showJavalinBanner = false;
            // Jetty defaults (min=8, max=250) are far too generous for 1-2 clients.
            QueuedThreadPool pool = new QueuedThreadPool(
                    config.maxJettyThreads, config.minJettyThreads, 60_000);
            pool.setName("jetty");
            cfg.jetty.threadPool = pool;
        });

        app.before(this::authFilter);

        app.get("/api/v1/info", ctx ->
                ctx.json(Map.of("version", "1.0")));

        app.get("/api/v1/maps/preview/*", ctx -> {
            String name = ctx.path().substring("/api/v1/maps/preview/".length());
            Path file = Paths.get("maps", name + ".MAP.png");
            Path mapFile = Paths.get("maps", name + ".MAP");
            boolean sourceExists = Files.exists(mapFile);
            if (!Files.exists(file)) {
                if (!sourceExists) {
                    ctx.status(404);
                    return;
                }
                try {
                    mapReader.writePreview(mapFile);
                } catch (IOException e) {
                    LOG.warn("Failed to generate preview for {}", mapFile, e);
                    ctx.status(404);
                    return;
                }
            } else if (sourceExists) {
                // Preview exists and its source is available — regenerate it if the source has
                // since changed. This is what makes eagerly resyncing every preview at startup
                // unnecessary: staleness is caught lazily, the first time a preview is requested.
                try {
                    if (!mapReader.previewMatches(mapFile)) {
                        mapReader.writePreview(mapFile);
                    }
                } catch (IOException e) {
                    LOG.warn("Failed to refresh stale preview for {}", mapFile, e);
                    // fall through and serve the existing (possibly stale) file
                }
            }
            if (notModified(ctx, file)) return;
            setCacheHeaders(ctx, file);
            ctx.contentType("image/png");
            ctx.result(Files.newInputStream(file));
        });

        app.get("/api/v1/maps", ctx -> {
            List<String> names = mapReader.paletteFiles().stream()
                    .map(RemoteServer::mapDisplayName)
                    .sorted()
                    .collect(Collectors.toList());
            ctx.json(names);
        });

        app.post("/api/v1/maps/*", ctx -> {
            String name = URLDecoder.decode(ctx.path().substring("/api/v1/maps/".length()), StandardCharsets.UTF_8);
            JsonNode body = mapper.readTree(ctx.body());
            if (!body.has("colors") || !body.get("colors").isArray() || body.get("colors").isEmpty()) {
                ctx.status(400).json(Map.of("error", "missing_colors"));
                return;
            }
            int[] colors = new int[body.get("colors").size()];
            int i = 0;
            for (JsonNode c : body.get("colors")) {
                try {
                    colors[i++] = parseHexColor(c.asText());
                } catch (NumberFormatException e) {
                    ctx.status(400).json(Map.of("error", "invalid_color"));
                    return;
                }
            }
            try {
                Path written = mapReader.write(name, colors);
                mapReader.writePreview(written);
                ctx.json(Map.of("name", name, "size", colors.length));
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("error", "invalid_name"));
            }
        });

        app.get("/api/v1/images/preview/*", ctx -> {
            String name = ctx.path().substring("/api/v1/images/preview/".length());
            Optional<Path> file = imageSource.findByDisplayName(name);
            if (file.isEmpty()) {
                ctx.status(404);
                return;
            }
            if (notModified(ctx, file.get())) return;
            setCacheHeaders(ctx, file.get());
            ctx.contentType("image/png");
            ctx.result(imageSource.loadThumbnail(file.get(), THUMBNAIL_MAX_DIM));
        });

        app.get("/api/v1/images", ctx -> ctx.json(imageLibrary.entries()));

        app.patch("/api/v1/images/*", ctx -> {
            String rest = URLDecoder.decode(ctx.path().substring("/api/v1/images/".length()), StandardCharsets.UTF_8);
            if (rest.endsWith("/rename")) {
                handleRenameImage(ctx, rest.substring(0, rest.length() - "/rename".length()));
            } else {
                handleUpdateImageMetadata(ctx, rest);
            }
        });

        app.get("/api/v1/videos/preview/*", ctx -> {
            String name = ctx.path().substring("/api/v1/videos/preview/".length());
            Optional<VideoEntry> entry = videoLibrary.findByFile(name);
            if (entry.isEmpty()) {
                ctx.status(404);
                return;
            }
            Path thumb;
            try {
                thumb = videoLibrary.thumbnailFile(entry.get(), THUMBNAIL_MAX_DIM);
            } catch (IOException e) {
                LOG.warn("Failed to generate video thumbnail for {}", name, e);
                ctx.status(404);
                return;
            }
            if (notModified(ctx, thumb)) return;
            setCacheHeaders(ctx, thumb);
            ctx.contentType("image/png");
            ctx.result(Files.newInputStream(thumb));
        });

        app.get("/api/v1/videos", ctx -> ctx.json(videoLibrary.entries()));

        // Full video bytes for the chapter-editing scrubber (unlike /preview/*, this stays behind
        // auth — authFilter's existing ?token= query-param fallback covers <video src> not being
        // able to set an Authorization header, same as it already does for SSE).
        app.get("/api/v1/videos/stream/*", ctx -> {
            String name = URLDecoder.decode(ctx.path().substring("/api/v1/videos/stream/".length()), StandardCharsets.UTF_8);
            Optional<VideoEntry> entry = videoLibrary.findByFile(name);
            if (entry.isEmpty()) {
                ctx.status(404);
                return;
            }
            Path videoPath = videoLibrary.pathOf(entry.get());
            ctx.writeSeekableStream(Files.newInputStream(videoPath), videoContentType(videoPath), Files.size(videoPath));
        });

        app.patch("/api/v1/videos/*", ctx -> {
            String rest = URLDecoder.decode(ctx.path().substring("/api/v1/videos/".length()), StandardCharsets.UTF_8);
            Optional<String[]> chapter = splitChapterPath(rest);
            if (chapter.isPresent()) {
                handlePatchChapter(ctx, chapter.get()[0], chapter.get()[1]);
            } else if (rest.endsWith("/rename")) {
                handleRenameVideo(ctx, rest.substring(0, rest.length() - "/rename".length()));
            } else {
                handleUpdateVideoMetadata(ctx, rest);
            }
        });

        app.post("/api/v1/videos/*", ctx -> {
            String rest = URLDecoder.decode(ctx.path().substring("/api/v1/videos/".length()), StandardCharsets.UTF_8);
            if (!rest.endsWith("/chapters")) {
                ctx.status(400).json(Map.of("error", "unknown_action"));
                return;
            }
            handleCreateChapter(ctx, rest.substring(0, rest.length() - "/chapters".length()));
        });

        app.delete("/api/v1/videos/*", ctx -> {
            String rest = URLDecoder.decode(ctx.path().substring("/api/v1/videos/".length()), StandardCharsets.UTF_8);
            Optional<String[]> chapter = splitChapterPath(rest);
            if (chapter.isEmpty()) {
                ctx.status(400).json(Map.of("error", "unknown_action"));
                return;
            }
            handleDeleteChapter(ctx, chapter.get()[0], chapter.get()[1]);
        });

        app.get("/api/v1/params", ctx ->
                ctx.json(serializer.serialize(paramRoot).toString()));

        app.get("/api/v1/params/*", ctx -> {
            String nodePath = extractNodePath(ctx);
            Optional<Node> nodeOpt = findNode(nodePath);
            if (nodeOpt.isEmpty()) {
                ctx.status(404).json(Map.of("error", "not_found"));
                return;
            }
            ctx.json(serializer.serialize(nodeOpt.get()).toString());
        });

        app.patch("/api/v1/params/*", ctx -> {
            String fullPath = extractNodePath(ctx);
            if (fullPath.endsWith("/animation")) {
                handlePatchAnimation(ctx, fullPath.substring(0, fullPath.length() - "/animation".length()));
                return;
            }
            Optional<String[]> trigger = splitTriggerPath(fullPath);
            if (trigger.isPresent()) {
                handlePatchTrigger(ctx, trigger.get()[0], trigger.get()[1]);
                return;
            }
            String nodePath = fullPath;
            Optional<Node> nodeOpt = findNode(nodePath);
            if (nodeOpt.isEmpty()) {
                ctx.status(404).json(Map.of("error", "not_found"));
                return;
            }
            Node node = nodeOpt.get();
            if (!node.isRemoteAllowed()) {
                ctx.status(403).json(Map.of("error", "not_allowed"));
                return;
            }
            JsonNode body = mapper.readTree(ctx.body());
            if (!body.has("value")) {
                ctx.status(400).json(Map.of("error", "missing_value"));
                return;
            }
            ParamValues.ApplyResult result = ParamValues.applyText(node, body.get("value").asText());
            switch (result) {
                case NOT_A_LEAF -> {
                    ctx.status(400).json(Map.of("error", "not_a_leaf"));
                    return;
                }
                case PARSE_ERROR -> {
                    ctx.status(400).json(Map.of("error", "invalid_value"));
                    return;
                }
                case OUT_OF_RANGE -> {
                    ctx.status(400).json(Map.of("error", "value_out_of_range"));
                    return;
                }
                case OK -> { /* fall through to response below */ }
            }
            ObjectNode response = serializer.serialize(node);
            if (node instanceof CompilableValue cv && cv.getLastCompileError() != null) {
                response.put("compileError", cv.getLastCompileError());
            }
            if (node instanceof AbstractValue param && param.isControlled()) {
                response.put("warning", "controlled_by_animator");
            }
            ctx.json(response.toString());
        });

        app.post("/api/v1/params/*", ctx -> {
            String fullPath = extractNodePath(ctx);

            if (fullPath.endsWith("/animation")) {
                handleCreateAnimation(ctx, fullPath.substring(0, fullPath.length() - "/animation".length()));
                return;
            }

            if (fullPath.endsWith("/triggers")) {
                handleCreateTrigger(ctx, fullPath.substring(0, fullPath.length() - "/triggers".length()));
                return;
            }

            if (fullPath.endsWith("/execute")) {
                String nodePath = fullPath.substring(0, fullPath.length() - "/execute".length());
                Optional<Node> nodeOpt = nodePath.isEmpty() ? Optional.of(paramRoot) : findNode(nodePath);
                if (nodeOpt.isEmpty()) {
                    ctx.status(404).json(Map.of("error", "not_found"));
                    return;
                }
                Node node = nodeOpt.get();
                if (!node.isRemoteAllowed()) {
                    ctx.status(403).json(Map.of("error", "not_allowed"));
                    return;
                }
                if (!(node instanceof Action action)) {
                    ctx.status(400).json(Map.of("error", "not_an_action"));
                    return;
                }
                action.execute(actionContext);
                ctx.json("{}");
                return;
            }

            if (!fullPath.endsWith("/randomise")) {
                ctx.status(400).json(Map.of("error", "unknown_action"));
                return;
            }
            String nodePath = fullPath.substring(0, fullPath.length() - "/randomise".length());
            Optional<Node> nodeOpt = nodePath.isEmpty() ? Optional.of(paramRoot) : findNode(nodePath);
            if (nodeOpt.isEmpty()) {
                ctx.status(404).json(Map.of("error", "not_found"));
                return;
            }
            Node node = nodeOpt.get();
            if (!node.isRemoteAllowed()) {
                ctx.status(403).json(Map.of("error", "not_allowed"));
                return;
            }
            node.randomise(new Random());
            if (node instanceof AbstractValue) {
                ctx.json(serializer.serialize(node).toString());
            } else {
                ctx.json("{}");
            }
        });

        app.delete("/api/v1/params/*", ctx -> {
            String fullPath = extractNodePath(ctx);
            if (fullPath.endsWith("/animation")) {
                handleDeleteAnimation(ctx, fullPath.substring(0, fullPath.length() - "/animation".length()));
                return;
            }
            Optional<String[]> trigger = splitTriggerPath(fullPath);
            if (trigger.isPresent()) {
                handleDeleteTrigger(ctx, trigger.get()[0], trigger.get()[1]);
                return;
            }
            ctx.status(400).json(Map.of("error", "unknown_action"));
        });

        app.sse("/api/v1/events", client -> {
            List<String> pathParams = client.ctx().queryParams("path");
            List<ParamNode> nodes;
            if (pathParams.isEmpty()) {
                // No filter: subscribe to the entire tree
                nodes = paramRoot instanceof ParamNode an ? List.of(an) : List.of();
            } else {
                nodes = pathParams.stream()
                        .map(this::findNode)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .filter(n -> n instanceof ParamNode)
                        .map(n -> (ParamNode) n)
                        .collect(Collectors.toList());
            }
            broadcaster.register(client, nodes);
            client.onClose(() -> broadcaster.unregister(client));
            client.keepAlive();
        });

        broadcaster.startFlushing(config.animationBroadcastIntervalMs);
        app.start(config.port);
        LOG.info("Remote server started on port {}", app.port());
    }

    public void stop() {
        broadcaster.stopFlushing();
        if (app != null) {
            app.stop();
        }
    }

    /** The port actually bound by the server (resolves {@code config.port == 0} to the ephemeral port picked by the OS). */
    public int port() {
        return app.port();
    }

    /** Resets the first-auth latch so the next QR scan triggers the onFirstAuth callback again. */
    public void resetFirstAuth() {
        firstAuthFired.set(false);
    }

    private void authFilter(Context ctx) {
        String path = ctx.path();
        // Only API paths require auth; static files and SPA root are public.
        if (!path.startsWith("/api/") || path.equals("/api/v1/info")
                || path.startsWith("/api/v1/maps/preview/") || path.startsWith("/api/v1/images/preview/")
                || path.startsWith("/api/v1/videos/preview/")) {
            return;
        }
        String auth = ctx.header("Authorization");
        String token = (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
        // Fall back to ?token= query param (required for SSE: EventSource can't set headers)
        if (token == null) {
            token = ctx.queryParam("token");
        }
        if (!tokenStore.validate(token)) {
            ctx.status(401).contentType("application/json").result("{\"error\":\"invalid_token\"}");
            throw new HttpResponseException(401, "invalid_token", Collections.emptyMap());
        }
        if (firstAuthFired.compareAndSet(false, true)) {
            Runnable r = onFirstAuth;
            if (r != null) r.run();
        }
    }

    /**
     * Sets Cache-Control/ETag headers so browsers can skip re-fetching preview images that
     * haven't changed on disk (the remote UI's image/map grids otherwise re-request every
     * thumbnail on each visit).
     */
    private void setCacheHeaders(Context ctx, Path file) throws IOException {
        ctx.header("Cache-Control", "public, max-age=604800");
        ctx.header("ETag", etagFor(file));
    }

    /** Returns true (and writes a 304 response) if the client's cached copy is still fresh. */
    private boolean notModified(Context ctx, Path file) throws IOException {
        String etag = etagFor(file);
        String ifNoneMatch = ctx.header("If-None-Match");
        if (etag.equals(ifNoneMatch)) {
            ctx.header("Cache-Control", "public, max-age=604800");
            ctx.header("ETag", etag);
            ctx.status(304);
            return true;
        }
        return false;
    }

    private String etagFor(Path file) throws IOException {
        return "\"" + Files.getLastModifiedTime(file).toMillis() + "-" + Files.size(file) + "\"";
    }

    private String extractNodePath(Context ctx) {
        String path = URLDecoder.decode(ctx.path(), StandardCharsets.UTF_8);
        if (path.startsWith(API_PARAMS_PREFIX)) {
            return path.substring(API_PARAMS_PREFIX.length());
        }
        return "";
    }

    private Optional<Node> findNode(String nodePath) {
        if (nodePath.isEmpty()) return Optional.of(paramRoot);
        return paramRoot.getChild(nodePath.split("/"));
    }

    /** Resolves {@code nodePath} to an {@link AbstractValue}, writing an error response and returning empty on failure. */
    private Optional<AbstractValue> resolveAnimatable(Context ctx, String nodePath) {
        Optional<Node> nodeOpt = findNode(nodePath);
        if (nodeOpt.isEmpty()) {
            ctx.status(404).json(Map.of("error", "not_found"));
            return Optional.empty();
        }
        Node node = nodeOpt.get();
        if (!node.isRemoteAllowed()) {
            ctx.status(403).json(Map.of("error", "not_allowed"));
            return Optional.empty();
        }
        if (!(node instanceof AbstractValue value) || !value.isAnimatable()) {
            ctx.status(400).json(Map.of("error", "not_animatable"));
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private void handleCreateAnimation(Context ctx, String nodePath) throws Exception {
        Optional<AbstractValue> targetOpt = resolveAnimatable(ctx, nodePath);
        if (targetOpt.isEmpty()) return;
        AbstractValue target = targetOpt.get();
        if (bindings.findContinuousBindingFor(nodePath).isPresent()) {
            ctx.status(409).json(Map.of("error", "already_animated"));
            return;
        }
        JsonNode body = mapper.readTree(ctx.body());
        String script = body.has("script") ? body.get("script").asText() : "";
        bindings.addContinuous(nodePath.replace('/', '›'), nodePath, script);
        broadcaster.broadcastAll("treeChanged", "{}");
        ctx.json(serializer.serialize(target).toString());
    }

    private void handlePatchAnimation(Context ctx, String nodePath) throws Exception {
        Optional<AbstractValue> targetOpt = resolveAnimatable(ctx, nodePath);
        if (targetOpt.isEmpty()) return;
        Optional<ContinuousBinding> bindingOpt = bindings.findContinuousBindingFor(nodePath);
        if (bindingOpt.isEmpty()) {
            ctx.status(404).json(Map.of("error", "not_animated"));
            return;
        }
        ContinuousBinding binding = bindingOpt.get();
        JsonNode body = mapper.readTree(ctx.body());
        if (body.has("script")) {
            binding.script.setValue(body.get("script").asText());
        }
        if (body.has("enabled")) {
            binding.enabled.setValue(body.get("enabled").asBoolean() ? 1 : 0);
        }
        broadcaster.broadcastAll("treeChanged", "{}");
        ctx.json(serializer.serialize(targetOpt.get()).toString());
    }

    private void handleDeleteAnimation(Context ctx, String nodePath) {
        Optional<AbstractValue> targetOpt = resolveAnimatable(ctx, nodePath);
        if (targetOpt.isEmpty()) return;
        Optional<ContinuousBinding> bindingOpt = bindings.findContinuousBindingFor(nodePath);
        if (bindingOpt.isEmpty()) {
            ctx.status(404).json(Map.of("error", "not_animated"));
            return;
        }
        bindings.removeBinding(bindingOpt.get());
        broadcaster.broadcastAll("treeChanged", "{}");
        ctx.json(serializer.serialize(targetOpt.get()).toString());
    }

    /**
     * Splits a request path ending in {@code .../triggers} or {@code .../triggers/{name}} into
     * {@code {nodePath, name}} (name empty for the bare {@code /triggers} form). Empty if the
     * path doesn't contain a {@code /triggers} segment at all.
     */
    private Optional<String[]> splitTriggerPath(String fullPath) {
        int idx = fullPath.indexOf("/triggers");
        if (idx < 0) return Optional.empty();
        String nodePath = fullPath.substring(0, idx);
        String rest = fullPath.substring(idx + "/triggers".length());
        if (rest.isEmpty()) return Optional.of(new String[] {nodePath, ""});
        if (!rest.startsWith("/") || rest.length() == 1) return Optional.empty();
        return Optional.of(new String[] {nodePath, rest.substring(1)});
    }

    /** Resolves {@code nodePath} to an {@link Action} or settable {@link AbstractValue}, writing an error response and returning empty on failure. */
    private Optional<Node> resolveTriggerable(Context ctx, String nodePath) {
        Optional<Node> nodeOpt = findNode(nodePath);
        if (nodeOpt.isEmpty()) {
            ctx.status(404).json(Map.of("error", "not_found"));
            return Optional.empty();
        }
        Node node = nodeOpt.get();
        if (!node.isRemoteAllowed()) {
            ctx.status(403).json(Map.of("error", "not_allowed"));
            return Optional.empty();
        }
        if (!(node instanceof Action) && !(node instanceof AbstractValue)) {
            ctx.status(400).json(Map.of("error", "not_triggerable"));
            return Optional.empty();
        }
        return Optional.of(node);
    }

    private void handleCreateTrigger(Context ctx, String nodePath) throws Exception {
        Optional<Node> targetOpt = resolveTriggerable(ctx, nodePath);
        if (targetOpt.isEmpty()) return;
        JsonNode body = mapper.readTree(ctx.body());
        String condition = body.has("condition") ? body.get("condition").asText() : "";
        double cooldown = body.has("cooldown")
                ? body.get("cooldown").asDouble()
                : EdgeTriggeredBinding.DEFAULT_COOLDOWN_SECONDS;
        String value = body.has("value") ? body.get("value").asText() : "";
        bindings.addEdgeTriggered(condition, nodePath, cooldown, value);
        broadcaster.broadcastAll("treeChanged", "{}");
        ctx.json(serializer.serialize(targetOpt.get()).toString());
    }

    private void handlePatchTrigger(Context ctx, String nodePath, String name) throws Exception {
        Optional<Node> targetOpt = resolveTriggerable(ctx, nodePath);
        if (targetOpt.isEmpty()) return;
        Optional<EdgeTriggeredBinding> bindingOpt = findTriggerOn(nodePath, name);
        if (bindingOpt.isEmpty()) {
            ctx.status(404).json(Map.of("error", "not_found"));
            return;
        }
        EdgeTriggeredBinding binding = bindingOpt.get();
        JsonNode body = mapper.readTree(ctx.body());
        if (body.has("condition")) {
            binding.condition.setValue(body.get("condition").asText());
        }
        if (body.has("cooldown")) {
            binding.cooldown.setValue(body.get("cooldown").asDouble());
        }
        if (body.has("value")) {
            binding.value.setValue(body.get("value").asText());
        }
        if (body.has("enabled")) {
            binding.enabled.setValue(body.get("enabled").asBoolean() ? 1 : 0);
        }
        broadcaster.broadcastAll("treeChanged", "{}");
        ctx.json(serializer.serialize(targetOpt.get()).toString());
    }

    private void handleDeleteTrigger(Context ctx, String nodePath, String name) {
        Optional<Node> targetOpt = resolveTriggerable(ctx, nodePath);
        if (targetOpt.isEmpty()) return;
        Optional<EdgeTriggeredBinding> bindingOpt = findTriggerOn(nodePath, name);
        if (bindingOpt.isEmpty()) {
            ctx.status(404).json(Map.of("error", "not_found"));
            return;
        }
        bindings.removeBinding(bindingOpt.get());
        broadcaster.broadcastAll("treeChanged", "{}");
        ctx.json(serializer.serialize(targetOpt.get()).toString());
    }

    /** Looks up an edge-triggered binding by name, rejecting a name that doesn't currently target {@code nodePath} (e.g. a stale client-side reference to a since-retargeted or deleted trigger). */
    private Optional<EdgeTriggeredBinding> findTriggerOn(String nodePath, String name) {
        return bindings.findEdgeTriggeredBindingByName(name)
                .filter(b -> b.target.getValue().equals(nodePath));
    }

    private void handleUpdateVideoMetadata(Context ctx, String file) throws IOException {
        Optional<VideoEntry> existingOpt = videoLibrary.findByFile(file);
        if (existingOpt.isEmpty()) {
            ctx.status(404).json(Map.of("error", "not_found"));
            return;
        }
        VideoEntry existing = existingOpt.get();
        JsonNode body = mapper.readTree(ctx.body());
        String title = body.has("title") ? body.get("title").asText() : existing.title();
        String source = body.has("source") ? body.get("source").asText() : existing.source();
        String license = body.has("license") ? body.get("license").asText() : existing.license();
        List<String> tags = existing.tags();
        if (body.has("tags") && body.get("tags").isArray()) {
            tags = new ArrayList<>();
            for (JsonNode t : body.get("tags")) tags.add(t.asText());
        }
        String defaultChapter = existing.defaultChapter();
        if (body.has("defaultChapter")) {
            defaultChapter = body.get("defaultChapter").isNull() ? null : body.get("defaultChapter").asText();
        }
        VideoEntry updated = videoLibrary.updateMetadata(file, title, tags, source, license, defaultChapter);
        ctx.json(updated);
    }

    private void handleRenameVideo(Context ctx, String file) throws IOException {
        if (videoLibrary.findByFile(file).isEmpty()) {
            ctx.status(404).json(Map.of("error", "not_found"));
            return;
        }
        JsonNode body = mapper.readTree(ctx.body());
        if (!body.has("file") || body.get("file").asText().isBlank()) {
            ctx.status(400).json(Map.of("error", "missing_file"));
            return;
        }
        try {
            VideoEntry updated = videoLibrary.rename(file, body.get("file").asText());
            ctx.json(updated);
        } catch (FileAlreadyExistsException e) {
            ctx.status(409).json(Map.of("error", "file_exists"));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", "invalid_filename"));
        }
    }

    /**
     * Splits a request path ending in {@code {file}/chapters} or {@code {file}/chapters/{name}}
     * into {@code {file, name}} (name empty for the bare {@code /chapters} form). Empty if the
     * path doesn't contain a {@code /chapters} segment at all.
     */
    private Optional<String[]> splitChapterPath(String rest) {
        int idx = rest.indexOf("/chapters");
        if (idx < 0) return Optional.empty();
        String file = rest.substring(0, idx);
        String tail = rest.substring(idx + "/chapters".length());
        if (tail.isEmpty() || !tail.startsWith("/") || tail.length() == 1) return Optional.empty();
        return Optional.of(new String[] {file, tail.substring(1)});
    }

    private void handleCreateChapter(Context ctx, String file) throws IOException {
        if (videoLibrary.findByFile(file).isEmpty()) {
            ctx.status(404).json(Map.of("error", "not_found"));
            return;
        }
        JsonNode body = mapper.readTree(ctx.body());
        if (!body.has("name") || body.get("name").asText().isBlank() || !body.has("start") || !body.has("end")) {
            ctx.status(400).json(Map.of("error", "missing_fields"));
            return;
        }
        try {
            VideoEntry updated = videoLibrary.addChapter(file, body.get("name").asText(),
                    body.get("start").asDouble(), body.get("end").asDouble());
            ctx.json(updated);
        } catch (IllegalStateException e) {
            ctx.status(409).json(Map.of("error", "chapter_exists"));
        }
    }

    private void handlePatchChapter(Context ctx, String file, String name) throws IOException {
        if (videoLibrary.findByFile(file).isEmpty()) {
            ctx.status(404).json(Map.of("error", "not_found"));
            return;
        }
        JsonNode body = mapper.readTree(ctx.body());
        String newName = body.has("name") ? body.get("name").asText() : null;
        Double start = body.has("start") ? body.get("start").asDouble() : null;
        Double end = body.has("end") ? body.get("end").asDouble() : null;
        try {
            VideoEntry updated = videoLibrary.updateChapter(file, name, newName, start, end);
            ctx.json(updated);
        } catch (NoSuchElementException e) {
            ctx.status(404).json(Map.of("error", "chapter_not_found"));
        }
    }

    private void handleDeleteChapter(Context ctx, String file, String name) throws IOException {
        if (videoLibrary.findByFile(file).isEmpty()) {
            ctx.status(404).json(Map.of("error", "not_found"));
            return;
        }
        try {
            VideoEntry updated = videoLibrary.deleteChapter(file, name);
            ctx.json(updated);
        } catch (NoSuchElementException e) {
            ctx.status(404).json(Map.of("error", "chapter_not_found"));
        }
    }

    private static String mapDisplayName(Path path) {
        String fn = path.getFileName().toString().toUpperCase();
        return fn.endsWith(".MAP") ? fn.substring(0, fn.length() - 4) : fn;
    }

    /** Parses a {@code "#rrggbb"} or {@code "rrggbb"} string into a packed {@code 0xRRGGBB} int. */
    private static int parseHexColor(String hex) {
        String stripped = hex.startsWith("#") ? hex.substring(1) : hex;
        if (stripped.length() != 6) {
            throw new NumberFormatException("Expected a 6-digit hex colour: " + hex);
        }
        return Integer.parseInt(stripped, 16);
    }

    private static String videoContentType(Path videoPath) {
        String name = videoPath.getFileName().toString().toLowerCase();
        if (name.endsWith(".mp4") || name.endsWith(".m4v")) return "video/mp4";
        if (name.endsWith(".ogv")) return "video/ogg";
        if (name.endsWith(".webm")) return "video/webm";
        return "application/octet-stream";
    }

    private void handleUpdateImageMetadata(Context ctx, String file) throws IOException {
        Optional<ImageEntry> existingOpt = imageLibrary.findByFile(file);
        if (existingOpt.isEmpty()) {
            ctx.status(404).json(Map.of("error", "not_found"));
            return;
        }
        ImageEntry existing = existingOpt.get();
        JsonNode body = mapper.readTree(ctx.body());
        String title = body.has("title") ? body.get("title").asText() : existing.title();
        String source = body.has("source") ? body.get("source").asText() : existing.source();
        String license = body.has("license") ? body.get("license").asText() : existing.license();
        List<String> tags = existing.tags();
        if (body.has("tags") && body.get("tags").isArray()) {
            tags = new ArrayList<>();
            for (JsonNode t : body.get("tags")) tags.add(t.asText());
        }
        ImageEntry updated = imageLibrary.updateMetadata(file, title, tags, source, license);
        ctx.json(updated);
    }

    private void handleRenameImage(Context ctx, String file) throws IOException {
        if (imageLibrary.findByFile(file).isEmpty()) {
            ctx.status(404).json(Map.of("error", "not_found"));
            return;
        }
        JsonNode body = mapper.readTree(ctx.body());
        if (!body.has("file") || body.get("file").asText().isBlank()) {
            ctx.status(400).json(Map.of("error", "missing_file"));
            return;
        }
        try {
            ImageEntry updated = imageLibrary.rename(file, body.get("file").asText());
            ctx.json(updated);
        } catch (FileAlreadyExistsException e) {
            ctx.status(409).json(Map.of("error", "file_exists"));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", "invalid_filename"));
        }
    }
}
