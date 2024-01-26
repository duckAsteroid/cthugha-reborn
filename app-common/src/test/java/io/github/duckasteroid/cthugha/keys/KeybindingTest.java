package io.github.duckasteroid.cthugha.keys;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class KeybindingTest {

  private static final String TEST_ID_1 = "TEST1";
  private static final Action TEST_ACTION_1 = Action.register(TEST_ID_1, "Test action 1", () -> System.out.println("1"));
  private static final String TEST_ID_2 = "TEST_2";

  private static final Action TEST_ACTION_2 = Action.register(TEST_ID_2, "Test action 2", () -> System.out.println("2"));

  @Test
  void parse() {
    Optional<Keybinding> parsed = Keybinding.parse("abc=_|CTRL+ESC=TEST1 ");
    assertTrue(parsed.isPresent());
    Keybinding keybinding = parsed.get();
    assertEquals(TEST_ID_1, keybinding.getAction().getId());

  }
}
