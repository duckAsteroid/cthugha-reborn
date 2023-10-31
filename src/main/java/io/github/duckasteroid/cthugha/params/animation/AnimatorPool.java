package io.github.duckasteroid.cthugha.params.animation;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class AnimatorPool {
  private final Map<String, Animator> animators = Collections.synchronizedMap(new HashMap<>());

  public void doAnimation(Duration clock) {
    animators.values().stream().forEach(a -> a.doAnimation(clock));
  }

  public void put(String key, Animator a) {
    animators.put(key, a);
  }

  public Animator remove(String key) {
    return animators.remove(key);
  }
}
