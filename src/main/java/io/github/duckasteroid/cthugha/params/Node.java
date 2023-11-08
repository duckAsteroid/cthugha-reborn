package io.github.duckasteroid.cthugha.params;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * This is a basic composite pattern for a DOM-like tree of parameters that can be tweaked.
 */
public interface Node {

  NodeType getNodeType();

  /**
   * Attempt to cast to a given type
   * @param clazz the desired type
   * @return this as that type
   * @param <T> desired type parameter
   */
  default <T extends AbstractValue> T asParam(Class<T> clazz) {
    if (getClass().isAssignableFrom(clazz)) {
      return (T) this;
    }
    throw new ClassCastException("Cannot cast "+getClass()+ " to "+clazz);
  }

  String getName();

  Node getParent();

  boolean hasParent();

  void randomise();

  /**
   * Provides a stream view of the path from this node via its parents
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

  Stream<Node> getChildren();

  default Optional<Node> getChild(String name) {
    return getChildren().filter(child -> child.getName().equals(name)).findFirst();
  }

  default Optional<Node> getChild(String[] path) {
    ArrayDeque<String> pathQ = new ArrayDeque<>(path.length);
    pathQ.addAll(Arrays.asList(path));
    return getChild(pathQ);
  }

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

  boolean isLeaf();

  void addChild(Node child);
  void removeChild(Node child);


}
