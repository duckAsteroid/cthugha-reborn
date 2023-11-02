package io.github.duckasteroid.cthugha.strings;

import io.github.duckasteroid.cthugha.JCthugha;
import io.github.duckasteroid.cthugha.ScreenBuffer;
import io.github.duckasteroid.cthugha.params.AffineTransformParams;
import io.github.duckasteroid.cthugha.params.Parameterized;
import io.github.duckasteroid.cthugha.params.RuntimeParameter;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public class StringRenderer implements Parameterized {
  private static final String DEFAULT = "PT10S";
  private static final Duration duration = JCthugha.config.getConfigAs(Constants.SECTION, Constants.KEY_DURATION, DEFAULT, Duration::parse);

  private final FontSource fontSource = new FontSource();

  private final RandomStringSource stringSource = new RandomStringSource();

  private Instant end;
  private Quote quote = null;
  private Font font;

  public final AffineTransformParams transformParams = new AffineTransformParams("Transform");

  public void show(ScreenBuffer buffer) {
    if (quote != null) {
      Graphics2D graphics = (Graphics2D) buffer.getBufferedImageView().getGraphics();
      graphics.setFont(font);
      FontMetrics fontMetrics = graphics.getFontMetrics();
      int length = fontMetrics.stringWidth(quote.quote());
      int height = fontMetrics.getHeight();
      char[] chars = quote.quote().toCharArray();
      graphics.setColor(buffer.getForegroundColor());

      AffineTransform tx = graphics.getTransform();
      tx = transformParams.applyTo(buffer.getDimensions(), tx);
      graphics.setTransform(tx);
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

  @Override
  public String getName() {
    return "String renderer";
  }

  @Override
  public Collection<RuntimeParameter> params() {
    return List.of();
  }
}
