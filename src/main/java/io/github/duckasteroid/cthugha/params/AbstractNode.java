package io.github.duckasteroid.cthugha.params;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Basic implementation of a composite parameter node, most nodes extend this rather than implement
 * the raw interface.
 */
public abstract class AbstractNode implements Node {

  private final String name;

  private Optional<Node> parent;

  private List<Node> children;

  public AbstractNode() {
    this.name = getClass().getSimpleName();
    this.children = new ArrayList<>();
  }

  public AbstractNode(String name) {
    this.name = name;
    this.children = new ArrayList<>();
  }

  /**
   * Set the children of this node using a collection
   */
  protected void initChildren(List<Node> children) {
    this.children = children;
  }

  /**
   * Set the children of this node using varargs
   */
  protected void initChildren(Node ... children) {
    this.children = Collections.unmodifiableList(Arrays.asList(children));
  }

  /**
   * Find all the fields in a given class that are parameters - use these as the children
   */
  protected void initFields(Class<?> clazz) {
    List<Node> list = Arrays.stream(clazz.getFields())
      .filter(field -> field.getType().isAssignableFrom(Node.class))
      .map(field -> {
        try {
          return Optional.ofNullable((Node) field.get(this));
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
  public String getName() {
    return name;
  }

  @Override
  public Node getParent() {
    return parent.orElse(null);
  }

  public void setParent(Node parent) {
    this.parent = Optional.ofNullable(parent);
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
    if (child instanceof AbstractNode someChild) someChild.setParent(this);
  }

  @Override
  public void removeChild(Node child) {
    children.remove(child);
    if (child instanceof AbstractNode someChild) someChild.setParent(null);
  }
}
