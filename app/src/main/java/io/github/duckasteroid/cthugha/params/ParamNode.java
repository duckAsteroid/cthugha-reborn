package io.github.duckasteroid.cthugha.params;

import io.github.duckasteroid.cthugha.params.action.AbstractAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Standard implementation of {@link Node} for interior (grouping) nodes.
 *
 * <p>Most components in Cthugha Reborn extend this class rather than implement {@link Node}
 * directly.  Subclasses declare their child parameters as {@code public} fields of type
 * {@link Node} and then register them in one of two ways:</p>
 * <ol>
 *   <li>Call {@link #initChildren(Node...)} or {@link #initChildren(List)} explicitly.</li>
 *   <li>Use the no-arg constructor, which calls {@link #initFields(Class)} to discover all
 *       {@code public} {@link Node}-typed fields via reflection.</li>
 * </ol>
 *
 * <p>The node's {@link #getName()} defaults to the simple class name when the no-arg
 * constructor is used, or to the {@code name} passed to the string constructor.</p>
 */
public abstract class ParamNode implements Node {

  private final String name;

  private Optional<Node> parent;

  private List<Node> children;

  private final Map<String, String> uiHints = new LinkedHashMap<>();

  private boolean remoteAllowed = true;

  /** Slash-delimited path from tree root to this node; computed lazily, cached after first access. */
  private volatile String cachedFullPath;

  private final CopyOnWriteArrayList<SubtreeChangeListener> subtreeListeners = new CopyOnWriteArrayList<>();

  /**
   * Creates a node whose name is the simple class name and whose children are all
   * {@code public} {@link Node}-typed fields discovered via {@link #initFields(Class)}.
   */
  public ParamNode() {
    this.name = getClass().getSimpleName();
    this.parent = Optional.empty();
    initFields(getClass());
  }

  /**
   * Creates a node with an explicit name and an initially empty child list.
   * Subclasses must call one of the {@code initChildren} methods after construction.
   *
   * @param name display name for this node
   */
  public ParamNode(String name) {
    this.name = name;
    this.parent = Optional.empty();
    this.children = new ArrayList<>();
  }

  /**
   * Replaces this node's child list with the given collection.
   *
   * @param children ordered list of child nodes
   */
  protected void initChildren(List<? extends Node> children) {
    this.children = new ArrayList<>(children);
    for (Node child : this.children) {
      if (child instanceof ParamNode an) an.setParent(this);
    }
  }

  /**
   * Replaces this node's child list with the given varargs, wrapped in a modifiable list.
   *
   * @param children child nodes in display order
   */
  protected void initChildren(Node ... children) {
    this.children = new ArrayList<>(Arrays.asList(children));
    for (Node child : this.children) {
      if (child instanceof ParamNode an) an.setParent(this);
    }
  }

  /**
   * Discovers child parameters by reflecting over the {@code public} fields of {@code clazz}
   * that are assignable to {@link Node}, then calls {@link #initChildren(List)} with them.
   * Fields that are {@code null} at the time of the call are silently skipped.
   *
   * @param clazz the class whose fields are inspected (typically {@code getClass()})
   */
  protected void initFields(Class<?> clazz) {
    List<Node> list = Arrays.stream(clazz.getFields())
      .filter(field -> Node.class.isAssignableFrom(field.getType()))
      .map(field -> {
        try {
          return Optional.ofNullable((Node) field.get(ParamNode.this));
        } catch (IllegalAccessException ignored) {
        }
        return Optional.ofNullable((Node)null);
      })
      .filter(Optional::isPresent)
      .map(Optional::get)
      .toList();
    initChildren(list);
  }


  @Override
  public NodeType getNodeType() {
    return NodeType.CONTAINER;
  }

  @Override
  public Map<String, String> getUiHints() {
    return Collections.unmodifiableMap(uiHints);
  }

  /**
   * Adds a UI hint entry and returns {@code this} for fluent construction.
   * See {@link UiHint} for standard keys and values.
   */
  public ParamNode withUiHint(String key, String value) {
    uiHints.put(key, value);
    return this;
  }

  /**
   * Marks this node as not accessible via the remote HTTP API and returns {@code this}
   * for fluent construction.  The remote server will return 403 for any request targeting
   * this node, and the serializer will omit it from the param tree entirely.
   */
  public ParamNode withNoRemote() {
    this.remoteAllowed = false;
    return this;
  }

  @Override
  public boolean isRemoteAllowed() {
    return remoteAllowed;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Node getParent() {
    return parent.orElse(null);
  }

  /**
   * Sets or clears this node's parent.  Called automatically by {@link #addChild} and
   * {@link #removeChild} on the parent node; avoid calling directly.
   *
   * @param parent the new parent, or {@code null} to detach
   */
  public void setParent(Node parent) {
    this.parent = Optional.ofNullable(parent);
    this.cachedFullPath = null;
  }

  /**
   * Returns the slash-delimited path from the tree root to this node, excluding the root name.
   * Computed lazily from parent references on first call and cached; safe to call at 60 fps.
   */
  public String getFullPath() {
    if (cachedFullPath == null) {
      if (!hasParent() || getParent() == null) {
        cachedFullPath = "";
      } else {
        String parentPath = ((ParamNode) getParent()).getFullPath();
        cachedFullPath = parentPath.isEmpty() ? getName() : parentPath + "/" + getName();
      }
    }
    return cachedFullPath;
  }

  /** Registers a listener that is called whenever any value-typed descendant of this node changes. */
  public void addSubtreeListener(SubtreeChangeListener listener) {
    subtreeListeners.add(listener);
  }

  /** Removes a previously registered subtree listener. */
  public void removeSubtreeListener(SubtreeChangeListener listener) {
    subtreeListeners.remove(listener);
  }

  /**
   * Called by leaf value nodes after their value changes.  Notifies subtree listeners on this
   * node and every ancestor, passing the full path of the changed node.
   */
  final void fireSubtreeListeners(String fullPath) {
    ParamNode current = this;
    while (current != null) {
      for (SubtreeChangeListener l : current.subtreeListeners) {
        l.changed(fullPath, this);
      }
      Node p = current.getParent();
      current = (p instanceof ParamNode an) ? an : null;
    }
  }

  @Override
  public boolean hasParent() {
    return parent.isPresent();
  }

  @Override
  public Stream<Node> getChildren() {
    return children.stream();
  }

  @Override
  public boolean isLeaf() {
    return children.isEmpty();
  }

  @Override
  public <T extends AbstractValue> T asParam(Class<T> clazz) {
    return null;
  }


  @Override
  public void addChild(Node child) {
    children.add(child);
    if (child instanceof ParamNode someChild) someChild.setParent(this);
  }

  @Override
  public void removeChild(Node child) {
    children.remove(child);
    if (child instanceof ParamNode someChild) someChild.setParent(null);
  }

  @Override
  public void randomise(Random rng) {
    children.forEach(child -> child.randomise(rng));
  }

  @Override
  public void resetToDefaults() {
    children.forEach(Node::resetToDefaults);
  }

  /**
   * Adds a "Reset" action child that calls {@link #resetToDefaults()} on this node.
   * Returns {@code this} for fluent construction.
   */
  public ParamNode withResetAction() {
    addChild(new AbstractAction("Reset", ctx -> this.resetToDefaults()));
    return this;
  }

  @Override
  public String toString() {
    return getName() + " [" + getNodeType().name() + "]: " +
      children.stream().map(Objects::toString)
        .collect(Collectors.joining(",\n", "{\n", "}"));
  }
}
