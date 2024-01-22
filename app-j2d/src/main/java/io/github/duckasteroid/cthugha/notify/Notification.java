package io.github.duckasteroid.cthugha.notify;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;

public class Notification {
  private int timeToLive;
  private double fadeOut = 255d;
  private final String message;
  private final Color color;
  private final Font font;

  public Notification(int timeToLive, String message, Color color, Font font) {
    this.timeToLive = timeToLive;
    this.message = message;
    this.color = color;
    this.font = font;
  }

  public boolean isExpired() {
    return timeToLive <= 0 && fadeOut < 0;
  }

  public int render(Dimension dimension, int yPosition, Graphics g2d) {
    Color renderColor = color;
    if (timeToLive < 0) {
      renderColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)fadeOut);
      fadeOut -= 3.0;
    }
    g2d.setColor(renderColor);
    g2d.setFont(font);
    int x = (dimension.width - width(g2d)) / 2;
    g2d.drawString(message, x, yPosition);
    timeToLive--;
    return height(g2d);
  }

  public int height(Graphics g2d) {
    return g2d.getFontMetrics().getHeight();
  }

  public int width(Graphics g2d) {
    char[] c = message.toCharArray();
    return g2d.getFontMetrics().charsWidth(c,0,c.length);
  }

  public void fadeOut() {
    timeToLive = 0;
  }
}
