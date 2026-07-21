package io.github.duckasteroid.cthugha.display.phase;

import com.asteroid.duck.opengl.util.color.StandardColors;
import org.joml.Vector4f;

import java.awt.Font;

/** Shared INI-parsing helpers used by display phases. */
class PhaseConfig {

    /** Parses "18", "18px", or "2%" of refHeight, capped at 120px to bound atlas size. */
    static int parseFontSize(String value, int refHeight) {
        return Math.min(parseDimension(value, refHeight), 120);
    }

    /** Parses "20", "20px", or "2%" of refDim — no cap. */
    static int parseDimension(String value, int refDim) {
        String v = value.trim();
        if (v.endsWith("%")) {
            double pct = Double.parseDouble(v.substring(0, v.length() - 1));
            return (int) Math.round(refDim * pct / 100.0);
        } else if (v.endsWith("px")) {
            return Integer.parseInt(v.substring(0, v.length() - 2).trim());
        } else {
            return Integer.parseInt(v);
        }
    }

    static int parseFontStyle(String s) {
        return switch (s.trim().toUpperCase()) {
            case "BOLD"        -> Font.BOLD;
            case "ITALIC"      -> Font.ITALIC;
            case "BOLD_ITALIC" -> Font.BOLD | Font.ITALIC;
            default            -> Font.PLAIN;
        };
    }

    /** Parses a StandardColors name (e.g. "YELLOW") or hex "#RRGGBB" / "#RRGGBBAA". */
    static Vector4f parseColor(String s, Vector4f fallback) {
        String v = s.trim().toUpperCase();
        try {
            return StandardColors.valueOf(v).color;
        } catch (IllegalArgumentException ignored) {}
        if (v.startsWith("#")) {
            String hex = v.substring(1);
            if (hex.length() == 6) {
                return new Vector4f(
                        Integer.parseInt(hex.substring(0, 2), 16) / 255f,
                        Integer.parseInt(hex.substring(2, 4), 16) / 255f,
                        Integer.parseInt(hex.substring(4, 6), 16) / 255f, 1f);
            } else if (hex.length() == 8) {
                return new Vector4f(
                        Integer.parseInt(hex.substring(0, 2), 16) / 255f,
                        Integer.parseInt(hex.substring(2, 4), 16) / 255f,
                        Integer.parseInt(hex.substring(4, 6), 16) / 255f,
                        Integer.parseInt(hex.substring(6, 8), 16) / 255f);
            }
        }
        return fallback;
    }

    private PhaseConfig() {}
}
