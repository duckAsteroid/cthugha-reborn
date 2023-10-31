package io.github.duckasteroid.cthugha.params;

import java.util.Collection;
import java.util.Collections;

/**
 * Interface to a thing that has runtime tunable parameters
 */
public interface Parameterized {
  String getName();
  Collection<RuntimeParameter> params();

  default Collection<Parameterized> children() {
    return Collections.emptyList();
  }
}
