package io.github.duckasteroid.cthugha.kaleidoscope;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;

public class Kaleidoscope {
  private int segments = 1;

  public void render(BufferedImage screenImage, Graphics g, Dimension dims) {
    if (segments > 1) {
      Graphics2D g2d = (Graphics2D) g;
      g.setColor(Color.MAGENTA);
      g.fillRect(0,0,dims.width, dims.height);
      AffineTransform initTx = g2d.getTransform();
      double segmentRads = (2 * Math.PI) / segments;
      double segmentDegs = Math.toDegrees(segmentRads);
      Point ctr = new Point(dims.width / 2, dims.height / 2);
      double h = Math.sqrt((ctr.x * ctr.x) + (ctr.y * ctr.y));
      int colorStep = 255 / segments;
      for(int i = 0; i < segments; i++) {
        double segmentRotateDegs = (segmentDegs / 2) - 90;
        ((Graphics2D) g).rotate(Math.toRadians(segmentRotateDegs), ctr.x, ctr.y);
        double startAngle = i * segmentDegs;
        Arc2D clip = new Arc2D.Double();
        // starts on X and extends anti clockwise
        clip.setArcByCenter(ctr.x, ctr.y, h, startAngle, segmentDegs, Arc2D.PIE);
        g2d.setClip(clip);
        int ch = i * colorStep;
        g.setColor(new Color(ch,ch,ch, colorStep));
        //g.fillRect(0,0,dims.width, dims.height);
        g.drawString("step="+i, 50,50);
        g.drawImage(screenImage, 0,0, dims.width, dims.height, null);
        g2d.rotate(-segmentRads, ctr.x, ctr.y);
      }
      g2d.setTransform(initTx);
    }
    else {
      g.drawImage(screenImage, 0, 0, dims.width, dims.height, null);
    }
    g.setClip(null);
  }

  public void increaseSegments() {
    segments++;
  }

  public void decreaseSegments() {
    segments = Math.max(1, segments - 1);
  }

  public String description() {
    return "kaleidoscope "+(segments <= 1 ? "off" : " segments="+segments);
  }
}
