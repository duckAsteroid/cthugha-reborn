package com.asteroid.duck.opengl.util.geom;

import org.joml.Vector4f;

import java.awt.Rectangle;

/**
 * The horizontal component of a @{@link Vertice}
 */
enum Horizontal {
	LEFT,
	RIGHT;

	public Float from(Vector4f vector4f) {
		return switch (this) {
			case LEFT -> vector4f.x;
			case RIGHT -> vector4f.z;
		};
	}

	public Integer from(Rectangle rect) {
		return switch (this) {
			case LEFT -> rect.x;
			case RIGHT -> rect.x + rect.width;
		};
	}
}
