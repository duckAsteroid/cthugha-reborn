package io.github.duckasteroid.cthugha.keys;

import java.awt.event.KeyEvent;
import java.util.function.Consumer;
import java.util.function.Function;

public class Keybind {
  private final char character;
  private final Character char2;
  private final String description;

  private final Consumer<KeyEvent> handler;

  public Keybind(char character, String description, Consumer<KeyEvent> handler) {
    this.character = character;
    this.char2 = null;
    this.description = description;
    this.handler = handler;
  }

  public Keybind(char character, char char2, String description, Consumer<KeyEvent> handler) {
    this.character = character;
    this.char2 = char2;
    this.description = description;
    this.handler = handler;
  }

  public boolean isFired(KeyEvent event) {
    return character == event.getKeyChar() || (char2 != null && char2.charValue() == event.getKeyChar());
  }

  public void handle(KeyEvent event) {
    handler.accept(event);
  }

  @Override
  public String toString() {
    return character + " = " + description;
  }
}
