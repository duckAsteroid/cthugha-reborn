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
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.AbstractValue;
import io.github.duckasteroid.cthugha.params.action.Action;
import io.github.duckasteroid.cthugha.params.action.ActionContext;
import io.github.duckasteroid.cthugha.params.Node;
import io.github.duckasteroid.cthugha.params.StringValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final TokenStore tokenStore;
    private final RemoteEventBroadcaster broadcaster;
    private final RemoteConfig config;
    private final ActionContext actionContext;
    private final ParamSerializer serializer;
    private final ObjectMapper mapper;

    private Javalin app;
    private volatile Runnable onFirstAuth;
    private final AtomicBoolean firstAuthFired = new AtomicBoolean(false);

    public RemoteServer(Node paramRoot, TokenStore tokenStore,
                        RemoteEventBroadcaster broadcaster, RemoteConfig config,
                        ActionContext actionContext) {
        this.paramRoot = paramRoot;
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
                ctx.status(404);
                return;
            }
            ctx.contentType("image/png");
            ctx.result(Files.newInputStream(file));
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
            String nodePath = extractNodePath(ctx);
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
                ctx.json(serializer.serialize(node).toString());
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
        LOG.info("Remote server started on port {}", config.port);
    }

    public void stop() {
        broadcaster.stopFlushing();
        if (app != null) {
            app.stop();
        }
    }

    /** Resets the first-auth latch so the next QR scan triggers the onFirstAuth callback again. */
    public void resetFirstAuth() {
        firstAuthFired.set(false);
    }

    private void authFilter(Context ctx) {
        String path = ctx.path();
        // Only API paths require auth; static files and SPA root are public.
        if (!path.startsWith("/api/") || path.equals("/api/v1/info") || path.startsWith("/api/v1/maps/preview/")) {
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
}
