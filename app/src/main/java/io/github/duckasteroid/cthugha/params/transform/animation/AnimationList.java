package io.github.duckasteroid.cthugha.params.transform.animation;

import io.github.duckasteroid.cthugha.params.DynamicChildList;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.action.AbstractAction;
import io.github.duckasteroid.cthugha.params.values.EnumParameter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A transform's "Animations": a stack of {@link AnimationPackage} instances, added from a picker
 * ({@link UiHint#ADD_LIST}, like the Wave tab's "New Wave") and each with a "Delete" action.
 * Instances are auto-named {@code "{Package} {N}"}; any number and any mix can be stacked. Each
 * frame {@link #apply} lets every package add its effect to a {@link TransformOffsets}, which
 * {@code TransformParams.applyTo()} combines with the transform's own values. The list
 * round-trips through screen configs as a {@link DynamicChildList}.
 *
 * <p>Packages are added and removed from remote-API threads while the GL thread iterates them,
 * hence the copy-on-write list.</p>
 */
public class AnimationList extends ParamNode implements DynamicChildList {

    /** Called after any list's packages change, to tell remote clients the tree changed. */
    private static volatile Runnable onTreeChanged = () -> {};

    private final CopyOnWriteArrayList<AnimationPackage> packages = new CopyOnWriteArrayList<>();
    private final Map<AnimationType, AtomicInteger> counters = new EnumMap<>(AnimationType.class);
    private final EnumParameter<AnimationType> newType =
            new EnumParameter<>("Animation", Arrays.asList(AnimationType.values()));

    public AnimationList() {
        super("Animations");
        withUiHint(UiHint.ICON, "sparkles");
        withUiHint(UiHint.CONTROL_TYPE, UiHint.ADD_LIST);
        withDescription("Animations stacked on this transform. Each moves it on top of the "
            + "settings above, so they combine and never overwrite them; Intensity 0 or Delete "
            + "returns it to exactly those settings.");
        for (AnimationType t : AnimationType.values()) {
            counters.put(t, new AtomicInteger());
        }
        newType.withUiHint(UiHint.CONTROL_TYPE, UiHint.LIST);
        newType.withDescription("Animation to add.");
        newType.withNoPersist();
        AbstractAction add = new AbstractAction("Add animation", ctx -> add(newType.getEnumeration()));
        add.withUiHint(UiHint.ICON, "circle-plus");
        initChildren(newType, add);
    }

    /** Registers the callback run whenever any transform's animations are added or removed. */
    public static void setOnTreeChanged(Runnable r) {
        onTreeChanged = r != null ? r : () -> {};
    }

    /** Adds a new package of {@code type}, auto-named {@code "{label} {N}"}. */
    public AnimationPackage add(AnimationType type) {
        return addNamed(type, type.label() + " " + counters.get(type).incrementAndGet());
    }

    private AnimationPackage addNamed(AnimationType type, String name) {
        AnimationPackage pkg = type.newInstance(name);
        AbstractAction delete = new AbstractAction("Delete", ctx -> remove(pkg));
        delete.withUiHint(UiHint.ICON, "trash-2");
        pkg.addChild(delete);
        packages.add(pkg);
        // The remote UI's add-list renders item containers above the picker wherever they sit.
        addChild(pkg);
        bumpCounterPast(type, name);
        onTreeChanged.run();
        return pkg;
    }

    /** Removes a package from the stack and the param tree. */
    public void remove(AnimationPackage pkg) {
        packages.remove(pkg);
        removeChild(pkg);
        onTreeChanged.run();
    }

    /** The stacked packages, in the order they were added. */
    public List<AnimationPackage> packages() {
        return List.copyOf(packages);
    }

    /**
     * Advances every package by {@code dt} seconds and adds its effect to {@code offsets}.
     * GL thread only.
     */
    public void apply(TransformOffsets offsets, double dt, float aspect) {
        for (AnimationPackage pkg : packages) {
            pkg.apply(offsets, dt, aspect);
        }
    }

    private void bumpCounterPast(AnimationType type, String name) {
        String prefix = type.label() + " ";
        if (!name.startsWith(prefix)) return;
        try {
            int n = Integer.parseInt(name.substring(prefix.length()).trim());
            counters.get(type).updateAndGet(cur -> Math.max(cur, n));
        } catch (NumberFormatException ignored) {
            // not an auto-generated name
        }
    }

    @Override
    public List<ChildSpec> describe() {
        List<ChildSpec> specs = new ArrayList<>(packages.size());
        for (AnimationPackage pkg : packages) {
            specs.add(new ChildSpec(pkg.getName(), pkg.type().name(), Map.of()));
        }
        return specs;
    }

    @Override
    public void recreate(List<ChildSpec> specs) {
        new ArrayList<>(packages).forEach(this::remove);
        for (ChildSpec spec : specs) {
            addNamed(AnimationType.valueOf(spec.type()), spec.name());
        }
    }
}
