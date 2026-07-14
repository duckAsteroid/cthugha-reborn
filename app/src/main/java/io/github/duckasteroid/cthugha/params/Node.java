package io.github.duckasteroid.cthugha.params;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A node in the hierarchical parameter tree.
 *
 * <p>Nodes follow the <em>Composite</em> design pattern: interior nodes group child nodes
 * and delegate operations (e.g. {@link #randomise()}) to them, while leaf nodes hold actual
 * parameter values.  Every node has a name, an optional parent, and zero or more children.</p>
 *
 * <p>The primary implementations are:</p>
 * <ul>
 *   <li>{@link ParamNode} – base class for interior nodes (type = {@link NodeType#CONTAINER}).</li>
 *   <li>{@link AbstractValue} – base class for leaf value nodes.</li>
 * </ul>
 */
public interface Node {

  /** Returns the kind of value this node holds (or {@link NodeType#CONTAINER} for grouping nodes). */
  NodeType getNodeType();

  /**
   * Casts this node to the given {@link AbstractValue} subtype.
   *
   * @param clazz the target leaf-node class
   * @param <T>   the target type
   * @return this node cast to {@code T}
   * @throws ClassCastException if this node is not an instance of {@code clazz}
   */
  default <T extends AbstractValue> T asParam(Class<T> clazz) {
    if (getClass().isAssignableFrom(clazz)) {
      return (T) this;
    }
    throw new ClassCastException("Cannot cast "+getClass()+ " to "+clazz);
  }

  /** Returns the display name of this node. */
  String getName();

  /**
   * Returns the UI hints map for this node. Keys and well-known values are defined in
   * {@link UiHint}. The returned map is unmodifiable. Defaults to an empty map.
   */
  default Map<String, String> getUiHints() {
    return Collections.emptyMap();
  }

  /** Returns the parent node, or {@code null} if this is the root. */
  Node getParent();

  /** Returns {@code true} if this node has a parent (i.e. is not the root). */
  boolean hasParent();

  /**
   * Sets this node's value (or all descendant values) to a random value within their
   * respective min/max ranges, using the supplied {@link Random} instance.
   *
   * @param rng the random source to use (typically {@code ctx.getRandom()})
   */
  void randomise(Random rng);

  /**
   * Returns a stream of ancestor nodes from this node's immediate parent up to the root,
   * in bottom-up order (parent first, root last).
   */
  default Stream<Node> getPath() {
    Iterator<Node> iter = new Iterator<Node>() {
      private Node current = Node.this;
      @Override
      public boolean hasNext() {
        return current.hasParent();
      }

      @Override
      public Node next() {
        current = current.getParent();
        return current;
      }
    };
    return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iter, 0), false);
  }

  /** Returns a stream over this node's direct children. */
  Stream<Node> getChildren();

  /**
   * Finds the first direct child whose {@link #getName()} equals {@code name}.
   *
   * @param name the child's name
   * @return the matching child, or empty if not found
   */
  default Optional<Node> getChild(String name) {
    return getChildren().filter(child -> child.getName().equals(name)).findFirst();
  }

  /**
   * Walks a path expressed as a name array, descending from this node.
   *
   * @param path ordered names of nodes to traverse, outermost first
   * @return the node at the end of the path, or empty if any segment is not found
   */
  default Optional<Node> getChild(String[] path) {
    ArrayDeque<String> pathQ = new ArrayDeque<>(path.length);
    pathQ.addAll(Arrays.asList(path));
    return getChild(pathQ);
  }

  /**
   * Walks a path expressed as a deque of names, consuming entries as it descends.
   * Returns {@code Optional.of(this)} when the deque is empty (base case).
   *
   * @param path remaining path segments to traverse
   * @return the node at the end of the path, or empty if any segment is not found
   */
  default Optional<Node> getChild(Deque<String> path) {
    if (path == null || path.isEmpty()) {
      return Optional.of(this);
    }
    final String target = path.removeFirst();
    Optional<Node> targetNode = getChild(target);
    if (targetNode.isPresent()) {
      return targetNode.get().getChild(path);
    }
    return Optional.empty();
  }

  /** Returns {@code true} if this node has no children. */
  boolean isLeaf();

  /**
   * Returns {@code true} if this node may be read or mutated via the remote HTTP API.
   * Defaults to {@code true}; set to {@code false} on nodes whose execution from a remote
   * client would be unsafe (e.g. Quit).  The remote server rejects requests targeting
   * non-allowed nodes with 403, and the serializer omits them from the param tree payload.
   */
  default boolean isRemoteAllowed() {
    return true;
  }

  /**
   * Returns a human-readable explanation of what this parameter does, or {@code null} if none
   * has been set. Surfaced in the remote UI as an on-demand hint (tap-to-expand, not a hover
   * tooltip, since the primary client is a phone). Purely descriptive — never parsed or relied
   * on by any code path.
   */
  default String getDescription() {
    return null;
  }

  /**
   * Adds {@code child} as a direct child of this node and sets this node as its parent.
   *
   * @param child the node to attach
   */
  void addChild(Node child);

  /**
   * Removes {@code child} from this node's children and clears its parent reference.
   *
   * @param child the node to detach
   */
  void removeChild(Node child);

  /**
   * Resets this node (and all descendants) to their constructor-time default values.
   * Interior nodes recurse; leaf value nodes restore their stored default.
   */
  default void resetToDefaults() {}


}
