package io.github.duckasteroid.cthugha.display;

import java.awt.Dimension;

/**
 * Works out the internal render buffer's size from the output (window, or monitor when
 * fullscreen) and the {@code [display]} settings:
 *
 * <ul>
 *   <li>{@code render_width} / {@code render_height} — an explicit size, in pixels
 *       ({@code "1280"}, {@code "1280px"}) or as a percentage of the current output
 *       ({@code "50%"}). Either one set overrides the cap; an unset one is 100% of the output.</li>
 *   <li>Otherwise the buffer has the output's shape, scaled down evenly to fit within
 *       {@code render_max} ({@code "WxH"}, default {@value #DEFAULT_MAX}; {@code "0"} = no cap).
 *       An output that already fits is used 1:1.</li>
 * </ul>
 */
final class RenderSize {

    static final String DEFAULT_MAX = "1920x1080";

    private RenderSize() {}

    /**
     * @param outputWidth  current output width in pixels
     * @param outputHeight current output height in pixels
     * @param renderWidth  {@code render_width} setting, or {@code null} if unset
     * @param renderHeight {@code render_height} setting, or {@code null} if unset
     * @param renderMax    {@code render_max} setting, or {@code null} for {@value #DEFAULT_MAX}
     */
    static Dimension compute(int outputWidth, int outputHeight,
                             String renderWidth, String renderHeight, String renderMax) {
        if (isSet(renderWidth) || isSet(renderHeight)) {
            return new Dimension(
                    Math.max(1, parseDimension(isSet(renderWidth) ? renderWidth : "100%", outputWidth)),
                    Math.max(1, parseDimension(isSet(renderHeight) ? renderHeight : "100%", outputHeight)));
        }
        Dimension cap = parseMax(isSet(renderMax) ? renderMax : DEFAULT_MAX);
        if (cap == null) {
            return new Dimension(Math.max(1, outputWidth), Math.max(1, outputHeight));
        }
        double scale = Math.min(1.0, Math.min(
                (double) cap.width / outputWidth, (double) cap.height / outputHeight));
        return new Dimension(
                Math.max(1, (int) Math.round(outputWidth * scale)),
                Math.max(1, (int) Math.round(outputHeight * scale)));
    }

    /** Parses "1280", "1280px", or "30%" into pixels relative to {@code reference}. */
    static int parseDimension(String value, int reference) {
        String v = value.trim();
        if (v.endsWith("%")) {
            return (int) Math.round(reference * Double.parseDouble(v.substring(0, v.length() - 1).trim()) / 100.0);
        }
        if (v.endsWith("px")) {
            v = v.substring(0, v.length() - 2).trim();
        }
        return Integer.parseInt(v);
    }

    /** Parses {@code "WxH"}, or returns {@code null} for {@code "0"} (no cap). */
    static Dimension parseMax(String value) {
        String v = value.trim().toLowerCase();
        if (v.equals("0")) return null;
        int x = v.indexOf('x');
        if (x < 0) throw new IllegalArgumentException("render_max must be WxH or 0, was: " + value);
        return new Dimension(Integer.parseInt(v.substring(0, x).trim()),
                Integer.parseInt(v.substring(x + 1).trim()));
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
