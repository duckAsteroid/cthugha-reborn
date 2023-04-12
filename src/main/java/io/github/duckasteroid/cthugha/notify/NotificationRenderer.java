package io.github.duckasteroid.cthugha.notify;

import io.github.duckasteroid.cthugha.ScreenBuffer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

public class NotificationRenderer {
  private final LinkedList<Notification> notifications = new LinkedList<>();

  private final int fadeThreshold;
  private final int max;
  private static Font DEFAULT_FONT = new Font("Timrs New Roman", Font.BOLD, 24);
  private static Color DEFAULT_COLOR = Color.white;

  public NotificationRenderer() {
    fadeThreshold = 8;
    max = 15;
  }

  public void render(Dimension dimension, Graphics g2d) {
    if (!notifications.isEmpty()) {
      int y = (dimension.height - height(g2d)) / 2;
      for(Notification n : new ArrayList<>(notifications)) {
        int dy = n.render(dimension, y, g2d);
        y += dy;
      }
      notifications.removeIf(Notification::isExpired);
    }
  }

  public int height(Graphics g2d) {
    return notifications.stream().mapToInt(n -> n.height(g2d)).sum();
  }

  public void notify(String message) {
    notifications.add(new Notification(100, message, DEFAULT_COLOR, DEFAULT_FONT));
    if (notifications.size() > fadeThreshold) {
      notifications.getFirst().fadeOut();
    }
    while(notifications.size() > max) {
      notifications.removeFirst();
    }
  }
}
