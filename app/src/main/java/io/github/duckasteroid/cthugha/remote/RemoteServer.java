package io.github.duckasteroid.cthugha.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpResponseException;
import io.javalin.http.staticfiles.Location;
import io.javalin.http.sse.SseClient;
import io.github.duckasteroid.cthugha.params.AbstractValue;
import io.github.duckasteroid.cthugha.params.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class RemoteServer {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteServer.class);
    private static final String API_PARAMS_PREFIX = "/api/v1/params/";

    private final Node paramRoot;
    private final TokenStore tokenStore;
    private final RemoteEventBroadcaster broadcaster;
    private final RemoteConfig config;
    private final ParamSerializer serializer;
    private final ObjectMapper mapper;

    private Javalin app;
    private volatile Runnable onFirstAuth;
    private final AtomicBoolean firstAuthFired = new AtomicBoolean(false);

    public RemoteServer(Node paramRoot, TokenStore tokenStore,
                        RemoteEventBroadcaster broadcaster, RemoteConfig config) {
        this.paramRoot = paramRoot;
        this.tokenStore = tokenStore;
        this.broadcaster = broadcaster;
        this.config = config;
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
        });

        app.before(this::authFilter);

        app.get("/api/v1/info", ctx ->
                ctx.json(Map.of("version", "1.0")));

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
            if (!(node instanceof AbstractValue param)) {
                ctx.status(400).json(Map.of("error", "not_a_leaf"));
                return;
            }
            JsonNode body = mapper.readTree(ctx.body());
            if (!body.has("value")) {
                ctx.status(400).json(Map.of("error", "missing_value"));
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
            node.randomise(new Random());
            if (node instanceof AbstractValue) {
                ctx.json(serializer.serialize(node).toString());
            } else {
                ctx.json("{}");
            }
        });

        app.sse("/api/v1/events", client -> {
            List<String> prefixes = client.ctx().queryParams("path");
            broadcaster.register(client, prefixes);
            client.onClose(() -> broadcaster.unregister(client));
            client.keepAlive();
        });

        app.start(config.port);
        LOG.info("Remote server started on port {}", config.port);
    }

    public void stop() {
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
        if (!path.startsWith("/api/") || path.equals("/api/v1/info")) {
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
        String path = ctx.path();
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
