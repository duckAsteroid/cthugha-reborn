package com.asteroid.duck.opengl.util.geom;

import org.joml.Vector2f;
import org.joml.Vector4f;

import java.awt.*;
import java.awt.Rectangle;
import java.util.stream.Stream;

/**
 * Represents one of the four corners of a rectangle.
 * With utilities for AWT coordinates and OpenGL ones
 */
public enum Vertice {
	TOP_LEFT(Vertical.TOP, Horizontal.LEFT),
	TOP_RIGHT(Vertical.TOP, Horizontal.RIGHT),
	BOTTOM_LEFT(Vertical.BOTTOM, Horizontal.LEFT),
	BOTTOM_RIGHT(Vertical.BOTTOM, Horizontal.RIGHT);
	/**
	 * The vertical component (top/bottom)
	 */
	private final Vertical vertical;
	/**
	 * The horizontal component (left/right)
	 */
	private final Horizontal horizontal;

	Vertice(Vertical vertical, Horizontal horizontal) {
		this.vertical = vertical;
		this.horizontal = horizontal;
	}

	/**
	 * Get this corner from a GL coordinate bounds vector.
	 * <pre>
	 *             END (Top Right)
	 *     +------(z,w)
	 *     |        |
	 *     |        |
	 *   (x,y)------+
	 *   START (Bottom Left)
	 * </pre>
	 * @param extent An bounds vector in open GL coordinates where 0,0 is bottom left
	 * @return Coordinates for this corner from the rectangle
	 */
	public Vector2f from(Vector4f extent) {
		assert(extent != null);
		return new Vector2f(horizontal.from(extent), vertical.from(extent));
	}
	/**
	 * Get this corner from an AWT bounds rectangle.
	 * <pre>
	 *  START (Top Left)
	 *   (x,y)------+
	 *     |        |
	 *     |        |
	 *     +------(w,h)
	 *   END (Bottom Right)
	 * </pre>
	 * @param rect An AWT bounds rectangle where 0,0 is top left
	 * @return Coordinates for this corner of the rectangle
	 */
	public Vector2f from(Rectangle rect) {
		assert(rect != null);
		return new Vector2f(horizontal.from(rect), vertical.from(rect));
	}

	public Point pointFrom(Rectangle awt) {
		assert(awt != null);
		return new Point(horizontal.from(awt), vertical.from(awt));
	}

	/**
	 * The standard six vertices that make up a regular GL rectangle in two triangles
	 * Clockwise around the edge starting Top Left
	 * @return TL, TR, BR, BR, BL, TL
	 */
	public static Stream<Vertice> standardSixVertices() {
		return Stream.of(TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT, TOP_LEFT);
	}
	/**
	 * The standard four vertices that make up a regular GL rectangle
	 * Clockwise around the edge starting Top Left
	 * @return TL, TR, BR, BL
	 */
	public static Stream<Vertice> standardFourVertices() {
		return Stream.of(TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT);
	}
}
