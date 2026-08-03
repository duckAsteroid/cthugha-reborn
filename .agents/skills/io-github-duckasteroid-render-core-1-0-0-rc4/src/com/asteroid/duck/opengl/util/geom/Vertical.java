package com.asteroid.duck.opengl.util.geom;

import org.joml.Vector4f;

import java.awt.Rectangle;

/**
 * The vertical component of a @{@link Vertice}
 */
enum Vertical  {
	TOP, BOTTOM;

	public Float from(Vector4f vector4f) {
		return switch (this) {
			case TOP -> vector4f.w;
			case BOTTOM -> vector4f.y;
		};
	}

	public Integer from(Rectangle rect) {
		return switch (this) {
			case TOP -> rect.y;
			case BOTTOM -> rect.y + rect.height;
		};
	}
}
