package io.github.duckasteroid.cthugha.keys;

import java.util.Optional;

public enum SpecialKey {
  ESC, DEL, ENTER, SPACE, FN_11, // add more as required
  // modifiers
  CTRL(true), SHIFT(true), ALT(true);

  private final boolean isModifier;

  SpecialKey(boolean isModifier) {
    this.isModifier = isModifier;
  }

  SpecialKey() {
    this(false);
  }

  public static Optional<SpecialKey> find(String key) {
    for(SpecialKey k : values()) {
      if (k.name().equals(key)) {
        return Optional.of(k);
      }
    }
    return Optional.empty();
  }

  public boolean isModifier() {
    return isModifier;
  }
}
