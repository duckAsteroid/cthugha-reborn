package io.github.duckasteroid.cthugha.audio;

/**
 * Reduces a multi dimensional point into a single (1D) point.
 * Or more specifically converts a multi channel audio sample into a single point value
 */
public interface PointValueExtractor {
  short value(short[] sample);
}
