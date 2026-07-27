package io.github.duckasteroid.cthugha.binding;

import com.asteroid.duck.opengl.util.timer.Clock;
import io.github.duckasteroid.cthugha.params.AbstractValue;
import io.github.duckasteroid.cthugha.params.DynamicChildList;
import io.github.duckasteroid.cthugha.params.Node;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.action.ActionContext;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Manages a single list of {@link Binding}s — the unification of what used to be two separate
 * managers, {@code AnimationSystem} and {@code TriggerSystem} — each pairing a compiled script
 * with a lazily path-resolved target (see {@link Binding}) in one of two evaluation modes
 * ({@link BindingMode#CONTINUOUS} or {@link BindingMode#EDGE_TRIGGERED}).
 *
 * <p>{@link ContinuousBinding}s publish themselves on their currently-resolved target leaf (see
 * {@link AbstractValue#getAnimationBinding()}), which the remote serializer renders inline on
 * that leaf's JSON via the dedicated {@code /animation} sub-resource routes.
 * {@link EdgeTriggeredBinding}s are published the same way, via
 * {@link io.github.duckasteroid.cthugha.remote.ParamSerializer} embedding a {@code triggers}
 * array on whatever node they currently target (an {@code Action} or
 * settable leaf), created/edited/deleted through the dedicated {@code /triggers} sub-resource
 * routes. Both kinds of binding are therefore marked {@link ParamNode#withNoRemote()} — this
 * node (the whole subtree) is as well, since it no longer has any remote-visible children of its
 * own and is purely an internal manager now.</p>
 *
 * <p>Implements {@link DynamicChildList} so a screen config can round-trip the current set of
 * bindings: {@link #describe()} captures each binding's mode, name, target and mode-specific
 * fields (script, or condition/cooldown/value), and {@link #recreate(List)} clears the current
 * list and rebuilds it via {@link #addContinuous} / {@link #addEdgeTriggered}, preserving each
 * binding's original name so leaf paths captured elsewhere in the same snapshot (e.g. {@code
 * Bindings/Trigger 1/condition}) resolve against the recreated child.</p>
 */
public class BindingSystem extends ParamNode implements DynamicChildList {

    public final BooleanParameter enabled = new BooleanParameter("Bindings", true);

    private final CopyOnWriteArrayList<Binding> bindings = new CopyOnWriteArrayList<>();
    /** Key/value state shared across every binding's script, exposed as {@code global.*}; see {@link ScriptHelpers}. */
    private final Map<String, Object> globalState = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger();

    private volatile Clock clock;
    private volatile Node root;
    private volatile ActionContext actionContext;
    private volatile Runnable onTreeChanged = () -> {};

    public BindingSystem() {
        super("Bindings");
        withNoRemote();

        enabled.withDescription("Master switch for every binding. When off, nothing ticks and no "
            + "trigger fires, regardless of its own enabled state.");
    }

    /**
     * Registers a {@link BindingMode#CONTINUOUS} binding targeting {@code targetPath}. The
     * binding appears as a child of this node (hidden from the generic remote tree — see class
     * docs — but reachable via the target leaf's {@code /animation} sub-resource). If the render
     * clock is already known (i.e. this is called after {@link #init}, as happens for a binding
     * created at runtime rather than at startup), the binding is initialised and its script
     * compiled immediately.
     *
     * @param name          display name for this binding in the param tree
     * @param targetPath    slash-delimited path of the parameter to animate, relative to the tree root
     * @param defaultScript initial expression, e.g. {@code "(sin(t * 0.31) + 1.0) / 2.0"}
     */
    public ContinuousBinding addContinuous(String name, String targetPath, String defaultScript) {
        ContinuousBinding binding = new ContinuousBinding(name, targetPath, defaultScript, globalState);
        binding.withNoRemote();
        register(binding);
        return binding;
    }

    /** Convenience overload for wiring a known {@link AbstractValue} directly (its full path is captured now, so the tree must already be fully wired — i.e. call after the target has a parent chain to the root). */
    public ContinuousBinding addContinuous(String name, AbstractValue target, String defaultScript) {
        return addContinuous(name, target.getFullPath(), defaultScript);
    }

    /**
     * Registers a {@link BindingMode#EDGE_TRIGGERED} binding, naming it uniquely for this
     * process's lifetime. If {@link #init} has already run, the binding is compiled and starts
     * ticking immediately.
     */
    public EdgeTriggeredBinding addEdgeTriggered(String condition, String targetPath, double cooldownSeconds, String value) {
        return addEdgeTriggeredNamed("Trigger " + counter.incrementAndGet(), condition, targetPath, cooldownSeconds, value);
    }

    /**
     * Same as {@link #addEdgeTriggered(String, String, double, String)} but with an explicit
     * name rather than an auto-generated {@code "Trigger N"} one — used by {@link #recreate(List)}
     * so a recreated binding's path matches the one a snapshot's leaf-value entries were captured
     * under. Also bumps the auto-naming counter past {@code name} if it looks like a {@code
     * "Trigger N"} name, so a later auto-named addition doesn't collide with it.
     */
    private EdgeTriggeredBinding addEdgeTriggeredNamed(String name, String condition, String targetPath,
                                                         double cooldownSeconds, String value) {
        EdgeTriggeredBinding binding = new EdgeTriggeredBinding(name, condition, targetPath, cooldownSeconds, value,
                globalState);
        binding.withNoRemote();
        register(binding);
        onTreeChanged.run();
        bumpCounterPast(name);
        return binding;
    }

    private void bumpCounterPast(String name) {
        if (name == null || !name.startsWith("Trigger ")) return;
        try {
            int n = Integer.parseInt(name.substring("Trigger ".length()).trim());
            counter.updateAndGet(cur -> Math.max(cur, n));
        } catch (NumberFormatException ignored) {
            // not an auto-generated name; nothing to bump
        }
    }

    private void register(Binding binding) {
        bindings.add(binding);
        addChild(binding);
        if (clock != null) {
            binding.init(clock, root, actionContext);
        }
    }

    /** Removes a binding, synchronously releasing any live target association and detaching it from the param tree. */
    public void removeBinding(Binding binding) {
        bindings.remove(binding);
        removeChild(binding);
        binding.release();
        onTreeChanged.run();
    }

    /** Returns the {@link BindingMode#CONTINUOUS} binding currently targeting {@code targetPath}, if any. */
    public Optional<ContinuousBinding> findContinuousBindingFor(String targetPath) {
        return bindings.stream()
                .filter(b -> b.mode == BindingMode.CONTINUOUS)
                .map(b -> (ContinuousBinding) b)
                .filter(b -> b.target.getValue().equals(targetPath))
                .findFirst();
    }

    /** Returns the {@link BindingMode#EDGE_TRIGGERED} binding with this exact name, if any — used to resolve the {@code {name}} segment of the {@code /triggers/{name}} REST routes. */
    public Optional<EdgeTriggeredBinding> findEdgeTriggeredBindingByName(String name) {
        return bindings.stream()
                .filter(b -> b.mode == BindingMode.EDGE_TRIGGERED)
                .map(b -> (EdgeTriggeredBinding) b)
                .filter(b -> b.getName().equals(name))
                .findFirst();
    }

    /**
     * Returns every {@link BindingMode#EDGE_TRIGGERED} binding currently targeting
     * {@code targetPath}, in registration order — unlike {@link #findContinuousBindingFor}, a
     * node can have more than one trigger, so {@link io.github.duckasteroid.cthugha.remote.ParamSerializer}
     * embeds this list wholesale as that node's {@code triggers} array.
     */
    public List<EdgeTriggeredBinding> findEdgeTriggeredBindingsFor(String targetPath) {
        return bindings.stream()
                .filter(b -> b.mode == BindingMode.EDGE_TRIGGERED)
                .map(b -> (EdgeTriggeredBinding) b)
                .filter(b -> b.target.getValue().equals(targetPath))
                .collect(Collectors.toList());
    }

    /** Key/value state shared across every binding's script; see {@link ScriptHelpers}. */
    public Map<String, Object> globalState() {
        return globalState;
    }

    /** Initialises all bindings with the render clock/tree root/action context. Call once from {@code init()}. */
    public void init(Clock clock, Node root, ActionContext actionContext) {
        this.clock = clock;
        this.root = root;
        this.actionContext = actionContext;
        bindings.forEach(b -> b.init(clock, root, actionContext));
    }

    /** Advances all active bindings by one frame. Call once per frame from the render loop. */
    public void tick() {
        if (!enabled.value) return;
        bindings.forEach(Binding::tick);
    }

    /** Registers a callback invoked whenever a binding is added or removed. */
    public void setOnTreeChanged(Runnable r) {
        this.onTreeChanged = r != null ? r : () -> {};
    }

    /**
     * {@inheritDoc}
     *
     * <p>Captures each binding's {@link BindingMode} as the {@code type} discriminator plus its
     * {@code target} and mode-specific fields ({@code script} for {@link ContinuousBinding};
     * {@code condition}/{@code cooldown}/{@code value} for {@link EdgeTriggeredBinding}) — enough
     * for {@link #recreate(List)} to rebuild an equivalent binding via {@link #addContinuous} /
     * {@link #addEdgeTriggered}.</p>
     */
    @Override
    public List<ChildSpec> describe() {
        List<ChildSpec> specs = new ArrayList<>(bindings.size());
        for (Binding b : bindings) {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("target", b.target.getValue());
            if (b instanceof ContinuousBinding cb) {
                fields.put("script", cb.script.getValue());
            } else if (b instanceof EdgeTriggeredBinding eb) {
                fields.put("condition", eb.condition.getValue());
                fields.put("cooldown", Double.toString(eb.cooldown.value));
                fields.put("value", eb.value.getValue());
            }
            specs.add(new ChildSpec(b.getName(), b.mode.name(), fields));
        }
        return specs;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Removes every current binding (via {@link #removeBinding}, so any live target
     * association is released synchronously) then rebuilds the list from {@code specs} in order,
     * dispatching on each spec's {@code type} ({@link BindingMode#CONTINUOUS} vs {@link
     * BindingMode#EDGE_TRIGGERED}) and preserving its original name.</p>
     */
    @Override
    public void recreate(List<ChildSpec> specs) {
        new ArrayList<>(bindings).forEach(this::removeBinding);
        for (ChildSpec spec : specs) {
            Map<String, String> fields = spec.fields();
            switch (BindingMode.valueOf(spec.type())) {
                case CONTINUOUS -> addContinuous(spec.name(), fields.getOrDefault("target", ""),
                        fields.getOrDefault("script", ""));
                case EDGE_TRIGGERED -> addEdgeTriggeredNamed(spec.name(), fields.getOrDefault("condition", ""),
                        fields.getOrDefault("target", ""),
                        parseDouble(fields.get("cooldown"), EdgeTriggeredBinding.DEFAULT_COOLDOWN_SECONDS),
                        fields.getOrDefault("value", ""));
            }
        }
    }

    private static double parseDouble(String s, double fallback) {
        if (s == null) return fallback;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
