package io.github.duckasteroid.cthugha.tab.generators;

import com.google.auto.service.AutoService;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.tab.TabGenerator;
import io.github.duckasteroid.cthugha.tab.TabMapping;

import java.util.Random;

/**
 * Straight passthrough: maps every destination pixel to itself, i.e. no displacement at all.
 * Selecting this switches off the tab-generator warp (spiral/melt/etc.) while the translate
 * step still runs each frame, so the blur/fade persistence trail is unaffected.
 */
@AutoService(TabGenerator.class)
public class NoDistortion extends ParamNode implements TabGenerator {

  public NoDistortion() {
    initChildren();
    withDescription("No pixel displacement — the translate step becomes a straight copy, "
        + "so blur/fade persistence trails still work but there is no spiral/melt/warp motion.");
  }

  @Override
  public TabMapping generate(int width, int height, Random rng) {
    return (dstX, dstY, dst, dstOffset, r) -> {
      dst.put(dstOffset,     (short) dstX);
      dst.put(dstOffset + 1, (short) dstY);
    };
  }
}
