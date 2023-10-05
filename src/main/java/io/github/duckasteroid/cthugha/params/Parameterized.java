package io.github.duckasteroid.cthugha.params;

import java.util.Collection;

/**
 * A thing that has runtime parameters
 */
public interface Parameterized {
  String getName();
  Collection<RuntimeParameter<?>> params();
}
