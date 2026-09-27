package io.github.duckasteroid.cthugha.tab.generators.lens;

import io.github.duckasteroid.cthugha.tab.*;
import com.google.auto.service.AutoService;

import static java.lang.Math.atan2;
import static java.lang.Math.sqrt;
import static java.lang.Math.PI;

import io.github.duckasteroid.cthugha.params.ParamNode;
import java.nio.ShortBuffer;
import java.util.Random;
import io.github.duckasteroid.cthugha.params.transform.XYParam;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;

/**
 * Anamorphic cylinder mirror: the classic trick used to draw an image that only resolves
 * correctly when reflected off a cylindrical mirror, run as a pixel remap. Unlike
 * {@link LogPolarZoom} (which zooms/spirals a polar image around itself), this fully changes
 * topology from rest: angle around the centre maps to horizontal source position, and radius
 * maps (with wraparound, via {@link #ringScale}) to vertical source position — so horizontal
 * bands of content become concentric rings, and a full swing of {@link #twist} spirals those
 * rings into a barber-pole tunnel.
 */
@AutoService(TabGenerator.class)
public class AnamorphicCylinder extends ParamNode implements TabGenerator {

  public XYParam center = new XYParam("Center", 0, 1, 0.5).withPadControl();
  /** How many times the source image repeats radially outward from the centre. */
  public DoubleParameter ringScale = new DoubleParameter("Ring Scale", 0.2, 6, 1.2);
  public DoubleParameter spin = new DoubleParameter("Spin", -Math.PI, Math.PI, 0);
  /** Couples angle to radius, spiralling the rings into a barber-pole twist. */
  public DoubleParameter twist = new DoubleParameter("Twist", -0.05, 0.05, 0);

  public AnamorphicCylinder() {
    super("Anamorphic Cylinder");
    initChildren(center, ringScale, spin, twist);
    withResetAction();

    center.withDescription("Centre point the rings radiate from, as a fraction of screen width/height.");
    ringScale.withDescription("How many times the source content repeats as concentric rings from the centre to the edge.");
    spin.withDescription("Rotates which angular sector is sampled, shifting the seam around the rings.");
    twist.withDescription("Couples angle to radius, spiralling the concentric rings into a barber-pole twist.");
  }

  @Override
  public TabMapping generate(int width, int height, Random rng) {
    double cx = width * center.x.value;
    double cy = height * center.y.value;
    double ring = ringScale.value;
    double sp = spin.value;
    double tw = twist.value;
    return (dstX, dstY, dst, dstOffset, r) -> {
      double dx = dstX - cx;
      double dy = dstY - cy;
      double radius = sqrt(dx * dx + dy * dy);
      double theta = atan2(dy, dx) + sp + tw * radius;
      int srcX = (int) (width * ((theta / (2 * PI)) + 0.5));
      int srcY = (int) (radius * ring);
      dst.put(dstOffset,     (short) TabGenerator.wrap(srcX, width));
      dst.put(dstOffset + 1, (short) TabGenerator.wrap(srcY, height));
    };
  }
}
