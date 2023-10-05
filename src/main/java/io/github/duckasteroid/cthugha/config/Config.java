package io.github.duckasteroid.cthugha.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.ini4j.Ini;
import java.io.File;
import org.ini4j.Profile;

public class Config {

  private final Ini iniFile;

  public Config(String iniFileName) throws IOException {
    File f = new File(Objects.requireNonNull(iniFileName, "INI File name"));
    this.iniFile = new Ini(f);
  }

  public String getConfig(String section, String key, String defaultValue) {
    return getSection(section).map(s -> s.get(key)).or(() -> Optional.of(defaultValue)).orElse(defaultValue);
  }

  public Optional<Profile.Section> getSection(String section) {
    if (iniFile.containsKey(section)) {
      return Optional.of(iniFile.get(section));
    }
    return Optional.empty();
  }

  public List<String> getConfigs(String section, String key, String[] defaultValue) {
    List<String> result = null;
    Optional<Profile.Section> theSection = getSection(section);
    if (theSection.isPresent()) {
      result = theSection.get().getAll(key);
    }

    if (result == null || result.isEmpty()) {
      return Arrays.asList(defaultValue);
    }
    return result;
  }

  public <T> T getConfigAs(String sect, String key, String defaultValue, Function<String, T> converter) {
    return converter.apply(getConfig(sect, key, defaultValue));
  }
}
