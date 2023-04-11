package io.github.duckasteroid.cthugha.notify;

import io.github.duckasteroid.cthugha.ScreenBuffer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.Iterator;
import java.util.LinkedList;

public class NotificationRenderer {
  private final LinkedList<Notification> notifications = new LinkedList<>();

  private static Font DEFAULT_FONT = new Font("Timrs New Roman", Font.BOLD, 24);
  private static Color DEFAULT_COLOR = Color.white;
  public void render(Graphics g2d) {
    if (!notifications.isEmpty()) {
      int y = (g2d.getClipBounds().y - height(g2d)) / 2;
      for(Notification n : notifications) {
        int dy = n.render(y, g2d);
        y += dy;
      }
      notifications.removeIf(Notification::isExpired);
    }
  }

  public int height(Graphics g2d) {
    return notifications.stream().mapToInt(n -> n.height(g2d)).sum();
  }

  public void notify(String message) {
    notifications.add(new Notification(2500,message, DEFAULT_COLOR, DEFAULT_FONT));
  }
}
