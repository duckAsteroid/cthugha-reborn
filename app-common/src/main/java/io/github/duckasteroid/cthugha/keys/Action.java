package io.github.duckasteroid.cthugha.keys;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A named piece of code (an action) that runs when the user clicks or presses something
 */
public class Action {
  private static final Map<String, Action> registry = new HashMap<>();

  private final String id;
  private final String description;
  private final Runnable handler;

  private Action(String id, String description, Runnable handler) {
    this.id = id;
    this.description = description;
    this.handler = handler;
  }

  public static Action register(String id, String description, Runnable handler) {
    Action action = new Action(id, description, handler);
    if (registry.containsKey(action.id)) {
      throw new IllegalArgumentException("id="+action.id+" is not unique, already registered");
    }
    registry.put(action.id, action);
    return action;
  }

  public static Action register(String description, Runnable handler) {
    return register(description.toUpperCase().replace(' ', '_'), description, handler);
  }

  public static Map<String, Action> getRegistry() {
    return Collections.unmodifiableMap(registry);
  }

  public String getId() {
    return id;
  }

  public String getDescription() {
    return description;
  }

  public void doAction() {
    handler.run();
  }
}
