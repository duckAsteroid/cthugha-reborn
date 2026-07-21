package io.github.duckasteroid.cthugha.display;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import org.junit.jupiter.api.Test;

class HtmlColorsTest {

  @Test
  public void testColorNames() {
    Color mediumVioletRed = HtmlColors.get("MediumVioletRed");
    assertNotNull(mediumVioletRed);
    assertEquals("java.awt.Color[r=199,g=21,b=133]", mediumVioletRed.toString());
    Color gainsboro = HtmlColors.get("Gainsboro");
    assertEquals("java.awt.Color[r=220,g=220,b=220]", gainsboro.toString());
  }
}
