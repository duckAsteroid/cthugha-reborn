package io.github.duckasteroid.cthugha.strings;

import io.github.duckasteroid.cthugha.JCthugha;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RandomStringSource {
  private final Random rnd = new Random();
  private static final String DEFAULT_STRINGS_FILE_NAME = "strings.txt";


  private final List<Quote> quotes;

  public RandomStringSource() {
    List<Quote> tmp;
    try (Stream<String> lines = Files.lines(Paths.get(JCthugha.config.getConfig(Constants.SECTION, Constants.KEY_STRINGS_FILE, DEFAULT_STRINGS_FILE_NAME)))) {
      tmp = lines.map(Quote::parse).collect(Collectors.toList());
    }
    catch (IOException ioe) {
      tmp = Collections.singletonList(new Quote("My programming is terrible", "Chris Senior"));
      ioe.printStackTrace();
    }
    this.quotes = tmp;
  }

  public Quote nextQuote() {
    return quotes.get(rnd.nextInt(quotes.size()));
  }

}
