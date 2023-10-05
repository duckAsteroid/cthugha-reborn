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
  private static final char START_QUOTE = '“';
  private static final char END_QUOTE = '”';

  public record Quote(String quote, String author) {
    public static Quote parse(String s) {
      int start = s.indexOf(START_QUOTE);
      if (start < 0) start = 0;
      int end = s.indexOf(END_QUOTE, start);
      if (end < 0) end = s.length();
      return new Quote(s.substring(start, end).trim(), s.substring(end).trim());
    }
  }

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
