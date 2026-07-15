package io.github.duckasteroid.cthugha.tab.generators.rotation;

import io.github.duckasteroid.cthugha.tab.*;
import com.google.auto.service.AutoService;

import static java.lang.Math.PI;
import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

import io.github.duckasteroid.cthugha.params.ParamNode;
import java.nio.ShortBuffer;
import java.util.Random;
import io.github.duckasteroid.cthugha.params.transform.XYParam;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;

/**
 * Pinwheel: divides the screen into N equal angular sectors around a centre and
 * independently rotates and zooms each one. Unlike {@link Kaleidoscope} the sectors are
 * NOT mirrored — each shows a rotated/zoomed view of its own wedge of the source, so
 * the image looks like a spinning fan or rotary shutter. With alternation enabled
 * adjacent sectors spin in opposite directions, reinforcing the pinwheel motion.
 */
@AutoService(TabGenerator.class)
public class Pinwheel extends ParamNode implements TabGenerator {

  public XYParam center = new XYParam("Center", 0, 1, 0.5).withPadControl();
  public IntegerParameter sectors = new IntegerParameter("Sectors", 2, 16, 6);
  /** Rotation applied to each sector, in radians. */
  public DoubleParameter rotationPerSector = new DoubleParameter("Rotation per sector (rad)", -PI, PI, PI / 6.0);
  /** Radial zoom within each sector. 1.0 = no zoom; >1 pulls content inward. */
  public DoubleParameter zoom = new DoubleParameter("Zoom", 0.5, 2.0, 1.0);
  /** Alternate rotation direction on odd-numbered sectors. */
  public BooleanParameter alternate = new BooleanParameter("Alternate", true);

  public Pinwheel() {
    super("Pinwheel");
    initChildren(center, sectors, rotationPerSector, zoom, alternate);
    withResetAction();

    center.withDescription("Centre point the screen is divided into sectors around.");
    sectors.withDescription("Number of angular sectors the screen is divided into.");
    rotationPerSector.withDescription("Rotation applied within each sector, in radians, giving the spinning-fan or rotary-shutter look.");
    zoom.withDescription("Radial zoom applied within each sector. 1.0 leaves it unchanged; values above 1 pull the sector's content inward toward the centre.");
    alternate.withDescription("Alternates the rotation direction on odd-numbered sectors, reinforcing the pinwheel motion.");
  }

  @Override
  public TabMapping generate(int width, int height, Random rng) {
    double cx = width  * center.x.value;
    double cy = height * center.y.value;
    double sectorAngle = 2.0 * PI / Math.max(sectors.value, 2);
    double rot = rotationPerSector.value;
    double zm = zoom.value;
    boolean alt = alternate.value;
    return (dstX, dstY, dst, dstOffset, r) -> {
      double dx = dstX - cx;
      double dy = dstY - cy;
      double radius = sqrt(dx * dx + dy * dy);
      double theta = atan2(dy, dx);
      double normTheta = ((theta % (2 * PI)) + 2 * PI) % (2 * PI);
      int sector = (int)(normTheta / sectorAngle);
      double rotation = (alt && (sector & 1) == 1) ? -rot : rot;
      double newTheta = theta + rotation;
      double newR = radius * zm;
      dst.put(dstOffset,     (short) TabGenerator.clamp((int)(cx + newR * cos(newTheta)), 0, width  - 1));
      dst.put(dstOffset + 1, (short) TabGenerator.clamp((int)(cy + newR * sin(newTheta)), 0, height - 1));
    };
  }
}
