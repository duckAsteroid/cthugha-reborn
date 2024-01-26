package io.github.duckasteroid.cthugha.keys;


import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A list of {@link Keybinding}s that we can match against given input events.
 * Knows how to dump "help" text for the keys
 */
public class KeyHandler {

  private final List<Keybinding> bindings;

  public KeyHandler(Stream<String> configFile) {
    this(Keybinding.parseConfig(configFile));
  }

  public KeyHandler(List<Keybinding> bindings) {
    this.bindings = bindings;
  }

  public Optional<Keybinding> match(Predicate<Keybinding> matcher) {
    if (matcher != null) {
      return bindings.stream().filter(matcher).findFirst();
    }
    return Optional.empty();
  }


  public record HelpLine(String plainKeys, String specialKeys, String actionDescription) {
    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      if (!plainKeys.isEmpty()) {
        builder.append(plainKeys);
        if (!specialKeys.isEmpty()) {
          builder.append("|");
        }
      }
      builder.append(specialKeys);
      builder.append('=').append(actionDescription);
      return builder.toString();
    }
  }
  public List<String> getHelpText() {
    Map<Action, List<Keybinding>> byAction =
      bindings.stream().collect(Collectors.groupingBy(Keybinding::getAction));

    return byAction.entrySet().stream().map(pair -> {
        final String characters = pair.getValue().stream()
          .map(Keybinding::getCharacters)
          .flatMap(Collection::stream)
          .map(Objects::toString)
          .collect(Collectors.joining());

        final String specialKeys = pair.getValue().stream()
          .map(Keybinding::getSpecialKeys)
          .flatMap(Collection::stream)
          .map(Objects::toString)
          .collect(Collectors.joining());

        return new HelpLine(characters,specialKeys,pair.getKey().getDescription());
      })
      .map(HelpLine::toString)
      .toList();

  }

}
