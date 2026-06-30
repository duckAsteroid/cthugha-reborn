package io.github.duckasteroid.cthugha.strings;

public record Quote(String quote, String author) {
  private static final char START_QUOTE = '“';
  private static final char END_QUOTE = '”';
  public static Quote parse(String s) {
    int start = s.indexOf(START_QUOTE);
    if (start < 0) {
      start = 0;
    }
    int end = s.indexOf(END_QUOTE, start);
    if (end < 0) {
      end = s.length();
    }
    return new Quote(s.substring(start, end).trim(), s.substring(end).trim());
  }
}
