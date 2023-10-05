package io.github.duckasteroid.cthugha.strings;

import io.github.duckasteroid.cthugha.JCthugha;
import io.github.duckasteroid.cthugha.ScreenBuffer;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

public class StringRenderer {
  private static final String DEFAULT = "PT5S";
  private static final Duration duration = JCthugha.config.getConfigAs(Constants.SECTION, Constants.KEY_DURATION, DEFAULT, Duration::parse);

  private final FontSource fontSource = new FontSource();

  private final RandomStringSource stringSource = new RandomStringSource();

  private Instant end;
  private RandomStringSource.Quote quote = null;
  private Font font;

  public void show(ScreenBuffer buffer) {
    if (quote != null) {
      Graphics graphics = buffer.getBufferedImageView().getGraphics();
      graphics.setFont(font);
      FontMetrics fontMetrics = graphics.getFontMetrics();
      int length = fontMetrics.stringWidth(quote.quote());
      int height = fontMetrics.getHeight();
      char[] chars = quote.quote().toCharArray();
      graphics.setColor(buffer.getForegroundColor());
      graphics.drawChars(chars,0, chars.length,100 , 100 + height);
      if (Instant.now().isAfter(end)) {
        quote = null;
      }
    }
  }

  public void begin() {
    end = Instant.now().plus(duration);
    font = fontSource.nextFont();
    quote = stringSource.nextQuote();
  }
}
