package io.github.duckasteroid.cthugha.strings;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Font;
import org.junit.jupiter.api.Test;

class FontSourceTest {

  @Test
  void parse() {
    final Font test = new Font(Font.DIALOG, Font.PLAIN, 14);
    String subject = test.getFontName();
    Font f = FontSource.parse(subject);
    assertNotNull(f);
    assertEquals(test.getFontName(), f.getName());
    assertEquals(FontSource.DEFAULT_SIZE, f.getSize());
    assertEquals(FontSource.Style.PLAIN.getCode(), f.getStyle());

    f = FontSource.parse(subject + ":24");
    assertNotNull(f);
    assertEquals(test.getFontName(), f.getName());
    assertEquals(24, f.getSize());
    assertEquals(FontSource.Style.PLAIN.getCode(), f.getStyle());

    f = FontSource.parse(subject + ":36:ITALIC");
    assertNotNull(f);
    assertEquals(test.getFontName(), f.getName());
    assertEquals(36, f.getSize());
    assertEquals(FontSource.Style.ITALIC.getCode(), f.getStyle());

  }
}
