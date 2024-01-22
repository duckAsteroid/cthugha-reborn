package io.github.duckasteroid.cthugha.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.ini4j.Ini;
import java.io.File;
import org.ini4j.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Config {


  private static final Logger LOG = LoggerFactory.getLogger(Config.class);
  public static final String CONFIG_FILENAME_PROPERTY = "iniFile";
  public static final String CONFIG_FILENAME = "cthugha";
  public static final String CONFIG_PROFILE = "profile";
  public static final String SUFFIX = ".ini";

  private final Ini iniFile;

  private final static Config SINGLETON = createSingleton();

  public static Config singleton() {
    return SINGLETON;
  }

  private static Config createSingleton() {
    final String filename_prefix = System.getProperty(CONFIG_FILENAME_PROPERTY, CONFIG_FILENAME);
    final String profile = System.getProperty(CONFIG_PROFILE, "");
    if (!profile.isBlank()) LOG.debug("Using profile "+profile);
    final String filename = (profile.isBlank()) ? filename_prefix + SUFFIX : filename_prefix + "-" + profile + SUFFIX;
    try {
      LOG.debug("Using config file "+filename);
      return new Config(filename);
    }
    catch (IOException ioe) {
      LOG.warn("Unable to read ini file "+filename);
      return new Config(new Ini());
    }
  }

  private Config(String iniFileName) throws IOException {
    File f = new File(Objects.requireNonNull(iniFileName, "INI File name"));
    if (f.exists()) {
      this.iniFile = new Ini(f);
    }
    else {
      InputStream inputStream = Config.class.getResourceAsStream(iniFileName);
      if (inputStream == null) {
        inputStream = Config.class.getResourceAsStream("./"+iniFileName);
      }
      if (inputStream == null) {
        throw new IOException("No classpath resource or file named "+iniFileName);
      }
      this.iniFile = new Ini(inputStream);
    }
  }

  public Config(Ini ini) {
    this.iniFile = ini;
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
