package io.github.duckasteroid.cthugha.keys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * THe binding of one or more characters or "keys" to a given action.
 * Press any of the keys and the action is run.
 */
public class Keybinding {
  private static final Logger LOG = LoggerFactory.getLogger(Keybinding.class);

  private final Set<Character> characters;

  private final Set<SpecialKey> specialKeys;

  private final Action action;

  public Keybinding(Action action, Set<Character> chars, Set<SpecialKey> specialKeys) {
    this.action = action;
    this.characters = chars;
    this.specialKeys = specialKeys;
  }

  public Keybinding(Action action, Character ... chars) {
    this.action = action;
    this.specialKeys = Collections.emptySet();
    this.characters = new HashSet<>(Arrays.asList(chars));
  }

  public Action getAction() {
    return action;
  }

  public Set<SpecialKey> getSpecialKeys() {
    return Collections.unmodifiableSet(specialKeys);
  }

  public Set<Character> getCharacters() {
    return Collections.unmodifiableSet(characters);
  }

  public static Optional<Keybinding> parse(String line) {
    int ix = line.lastIndexOf("=");
    if (ix > 0) {
      final String keysString = line.substring(0, ix);
      final String actionString = line.substring(ix + 1);
      Action action = Action.getRegistry().get(actionString.trim());
      if (action != null) {
        Set<Character> characters = new HashSet<>();
        Set<SpecialKey> specialKeys = new HashSet<>();
        List<String> keys = extractKeys(keysString);
        // first pass through keys - find special chars and remove
        Iterator<String> iter = keys.iterator();
        while(iter.hasNext()) {
          String key = iter.next();
          Optional<SpecialKey> optSpecial = SpecialKey.find(key);
          if (optSpecial.isPresent()) {
            iter.remove();
            specialKeys.add(optSpecial.get());
          }
        }
        // second pass - normal chars
        for(String s : keys) {
          s.chars().mapToObj(i -> (char) i).forEach(characters::add);
        }

        // create binding
        if (!characters.isEmpty() || !specialKeys.isEmpty()) {
          return Optional.of(new Keybinding(action, characters, specialKeys));
        }
        else {
          LOG.warn("No keys bound for action "+actionString);
        }
      }
    }
    return Optional.empty();
  }


  private static List<String> extractKeys(String keyString) {
    return new ArrayList<>(Arrays.asList(keyString.split("[ +|]")));
  }

  public static List<Keybinding> parseConfig(Stream<String> config) {
    return config.map(Keybinding::parse)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(Collectors.toList());
  }

  @Override
  public String toString() {
    return characters.stream().map(Object::toString).collect(Collectors.joining()) + "|" +
      specialKeys.stream().map(Object::toString).collect(Collectors.joining("+")) +
      "=" + action.getId();
  }
}
