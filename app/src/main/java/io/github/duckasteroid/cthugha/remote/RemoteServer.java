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
import io.github.duckasteroid.cthugha.animation.AnimationBinding;
import io.github.duckasteroid.cthugha.animation.AnimationSystem;
import io.github.duckasteroid.cthugha.img.RandomImageSource;
import io.github.duckasteroid.cthugha.map.MapFileReader;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.AbstractValue;
import io.github.duckasteroid.cthugha.params.action.Action;
import io.github.duckasteroid.cthugha.params.action.ActionContext;
import io.github.duckasteroid.cthugha.params.CompilableValue;
import io.github.duckasteroid.cthugha.params.Node;
import io.github.duckasteroid.cthugha.params.StringValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class RemoteServer {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteServer.class);
    private static final String API_PARAMS_PREFIX = "/api/v1/params/";

    private final Node paramRoot;
    private final AnimationSystem animation;
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
    private final MapFileReader mapReader = new MapFileReader(Paths.get("maps"));
    private static final int THUMBNAIL_MAX_DIM = 240;

    public RemoteServer(Node paramRoot, AnimationSystem animation, TokenStore tokenStore,
                        RemoteEventBroadcaster broadcaster, RemoteConfig config,
                        ActionContext actionContext) {
        this.paramRoot = paramRoot;
        this.animation = animation;
        this.tokenStore = tokenStore;
        this.broadcaster = broadcaster;
        this.config = config;
        this.actionContext = actionContext;
        this.serializer = new ParamSerializer();
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
            if (!Files.exists(file)) {
                Path mapFile = Paths.get("maps", name + ".MAP");
                if (!Files.exists(mapFile)) {
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
            }
            if (notModified(ctx, file)) return;
            setCacheHeaders(ctx, file);
            ctx.contentType("image/png");
            ctx.result(Files.newInputStream(file));
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
            if (node instanceof StringValue sv) {
                sv.setValue(body.get("value").asText());
                ObjectNode response = serializer.serialize(node);
                if (sv instanceof CompilableValue cv && cv.getLastCompileError() != null) {
                    response.put("compileError", cv.getLastCompileError());
                }
                ctx.json(response.toString());
                return;
            }
            if (!(node instanceof AbstractValue param)) {
                ctx.status(400).json(Map.of("error", "not_a_leaf"));
                return;
            }
            double value = body.get("value").asDouble();
            double min = param.getMin().doubleValue();
            double max = param.getMax().doubleValue();
            if (value < min || value > max) {
                ctx.status(400).json(Map.of("error", "value_out_of_range"));
                return;
            }
            param.setValue(value);
            ObjectNode response = serializer.serialize(node);
            if (param.isControlled()) {
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
            if (!fullPath.endsWith("/animation")) {
                ctx.status(400).json(Map.of("error", "unknown_action"));
                return;
            }
            handleDeleteAnimation(ctx, fullPath.substring(0, fullPath.length() - "/animation".length()));
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
                || path.startsWith("/api/v1/maps/preview/") || path.startsWith("/api/v1/images/preview/")) {
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
        if (animation.findBindingFor(target).isPresent()) {
            ctx.status(409).json(Map.of("error", "already_animated"));
            return;
        }
        JsonNode body = mapper.readTree(ctx.body());
        String script = body.has("script") ? body.get("script").asText() : "";
        animation.addBinding(target.getFullPath().replace('/', '›'), target, script);
        broadcaster.broadcastAll("treeChanged", "{}");
        ctx.json(serializer.serialize(target).toString());
    }

    private void handlePatchAnimation(Context ctx, String nodePath) throws Exception {
        Optional<AbstractValue> targetOpt = resolveAnimatable(ctx, nodePath);
        if (targetOpt.isEmpty()) return;
        Optional<AnimationBinding> bindingOpt = animation.findBindingFor(targetOpt.get());
        if (bindingOpt.isEmpty()) {
            ctx.status(404).json(Map.of("error", "not_animated"));
            return;
        }
        AnimationBinding binding = bindingOpt.get();
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
        Optional<AnimationBinding> bindingOpt = animation.findBindingFor(targetOpt.get());
        if (bindingOpt.isEmpty()) {
            ctx.status(404).json(Map.of("error", "not_animated"));
            return;
        }
        animation.removeBinding(bindingOpt.get());
        broadcaster.broadcastAll("treeChanged", "{}");
        ctx.json(serializer.serialize(targetOpt.get()).toString());
    }
}
