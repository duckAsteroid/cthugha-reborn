package io.github.duckasteroid.cthugha.strings;

import io.github.duckasteroid.cthugha.JCthugha;
import io.github.duckasteroid.cthugha.config.Config;
import java.awt.Font;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

public class FontSource {
  private static final String[] FALLBACK_FONTS = new String[]{"Dialog:48:PLAIN"};
  public static final int DEFAULT_SIZE = 12;
  private final Random rnd = new Random();
  private final Map<String, Font> cache = new HashMap<>();
  private final List<String> fontNames;

  enum Style {

    PLAIN(Font.PLAIN), BOLD(Font.BOLD), ITALIC(Font.ITALIC);

    private final int code;
    Style(int code) {
      this.code = code;
    }
    public int getCode() {
      return code;
    }
  }

  public FontSource() {
    fontNames = Config.singleton().getConfigs(Constants.SECTION,Constants.KEY_FONTS, FALLBACK_FONTS);
  }

  public static Font parse(final String s) {
    final String[] sections = s.split(":");
    Font f = Font.getFont(Font.DIALOG);
    if (sections.length > 0) {
      String name = sections[0];
      Style style = Style.PLAIN;
      int size = DEFAULT_SIZE;
      if (sections.length > 1) {
        size = Integer.parseInt(sections[1]);
        if (sections.length > 2) {
          style = Style.valueOf(sections[2]);
        }
      }
      f = new Font(name, style.getCode(), size);
    }
    return f;
  }

  public Font nextFont() {
    final String fontName = nextFontName();
    if (!cache.containsKey(fontName)) {
      cache.put(fontName, parse(fontName));
    }
    return cache.get(fontName);
  }

  public String nextFontName() {
    return fontNames.get(rnd.nextInt(fontNames.size()));
  }

}
