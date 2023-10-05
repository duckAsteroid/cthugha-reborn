package io.github.duckasteroid.cthugha.params;

import java.util.function.Predicate;

public abstract class RuntimeParameter<T> {
  public enum Type {
    BOOLEAN, DOUBLE, INTEGER
  }
  private final String description;

  public RuntimeParameter(String description) {
    this.description = description;
  }

  public abstract Type getType();

  public abstract T getValue();

  public String getDescription() {
    return description;
  }

}
