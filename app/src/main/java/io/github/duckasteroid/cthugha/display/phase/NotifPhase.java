package io.github.duckasteroid.cthugha.display.phase;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.color.StandardColors;
import com.asteroid.duck.opengl.util.resources.font.FontTexture;
import com.asteroid.duck.opengl.util.resources.font.FontTextureFactory;
import com.asteroid.duck.opengl.util.renderaction.RenderActionQueue;
import com.asteroid.duck.opengl.util.text.StringRenderer;
import io.github.duckasteroid.cthugha.JCthugha;
import io.github.duckasteroid.cthugha.config.Config;
import io.github.duckasteroid.cthugha.params.ContainerNode;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.awt.Font;
import java.awt.Rectangle;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import static org.lwjgl.opengl.GL11.*;

/**
 * Renders transient notification messages as an RGBA screen overlay.
 * Notifications are polled from {@link JCthugha#pollNotification()} each frame and
 * displayed for a configurable duration before disappearing.
 *
 * <p>Configurable in the {@code [Notifications]} INI section:
 * {@code font}, {@code size}, {@code style}, {@code color}, {@code x}, {@code y}, {@code duration}.
 */
public class NotifPhase implements RenderPhase {

    private static final String SECTION = "Notifications";
    private static final Config CFG = Config.singleton();

    private final JCthugha cthugha;
    private StringRenderer notifRenderer;
    private Instant notifExpiry = null;
    private Duration notifDuration;

    public NotifPhase(JCthugha cthugha) {
        this.cthugha = cthugha;
    }

    @Override
    public void init(RenderContext ctx) throws IOException {
        Rectangle win = ctx.getWindow();

        String fontName = CFG.getConfig(SECTION, "font",  "Monospaced");
        String sizeStr  = CFG.getConfig(SECTION, "size",  "18");
        String styleStr = CFG.getConfig(SECTION, "style", "BOLD");
        int size  = parseFontSize(sizeStr, win.height);
        int style = parseFontStyle(styleStr);

        float x = CFG.getConfigAs(SECTION, "x", "20", Float::parseFloat);
        float y = CFG.getConfigAs(SECTION, "y", "30", Float::parseFloat);

        Vector4f color = parseColor(CFG.getConfig(SECTION, "color", "YELLOW"));

        notifDuration = Duration.ofSeconds(
                CFG.getConfigAs(SECTION, "duration", "3", Integer::parseInt));

        FontTexture font = new FontTextureFactory(new Font(fontName, style, size), true).createFontTexture();
        notifRenderer = new StringRenderer(font);
        notifRenderer.init(ctx);
        notifRenderer.setTransform(new Matrix4f().translate(x, y, 0.0f));
        notifRenderer.setTextColor(color);
    }

    @Override
    public void screenRender(RenderContext ctx) {
        String notif = cthugha.pollNotification();
        if (notif != null) {
            notifRenderer.setText(notif);
            notifExpiry = Instant.now().plus(notifDuration);
        }
        if (notifExpiry == null || Instant.now().isAfter(notifExpiry)) {
            notifExpiry = null;
            return;
        }
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        notifRenderer.doRender(ctx);
        glDisable(GL_BLEND);
    }

    @Override
    public void dispose() {
        if (notifRenderer != null) notifRenderer.dispose();
    }

    // No param-tree actions — notifications are produced by other components calling cthugha.notify().
    @Override
    public void registerActions(ContainerNode generalGroup, RenderActionQueue renderActions) {}

    /** Parses "18", "18px", or "2%" (capped at 120px to bound atlas size). */
    private static int parseFontSize(String value, int refHeight) {
        String v = value.trim();
        int px;
        if (v.endsWith("%")) {
            double pct = Double.parseDouble(v.substring(0, v.length() - 1));
            px = (int) Math.round(refHeight * pct / 100.0);
        } else if (v.endsWith("px")) {
            px = Integer.parseInt(v.substring(0, v.length() - 2).trim());
        } else {
            px = Integer.parseInt(v);
        }
        return Math.min(px, 120);
    }

    private static int parseFontStyle(String s) {
        return switch (s.toUpperCase()) {
            case "BOLD"        -> Font.BOLD;
            case "ITALIC"      -> Font.ITALIC;
            case "BOLD_ITALIC" -> Font.BOLD | Font.ITALIC;
            default            -> Font.PLAIN;
        };
    }

    /** Parses a StandardColors name (e.g. "YELLOW") or hex "#RRGGBB" / "#RRGGBBAA". */
    private static Vector4f parseColor(String s) {
        String v = s.trim().toUpperCase();
        try {
            return StandardColors.valueOf(v).color;
        } catch (IllegalArgumentException ignored) {}
        if (v.startsWith("#")) {
            String hex = v.substring(1);
            if (hex.length() == 6) {
                int r = Integer.parseInt(hex.substring(0, 2), 16);
                int g = Integer.parseInt(hex.substring(2, 4), 16);
                int b = Integer.parseInt(hex.substring(4, 6), 16);
                return new Vector4f(r / 255f, g / 255f, b / 255f, 1f);
            } else if (hex.length() == 8) {
                int r = Integer.parseInt(hex.substring(0, 2), 16);
                int g = Integer.parseInt(hex.substring(2, 4), 16);
                int b = Integer.parseInt(hex.substring(4, 6), 16);
                int a = Integer.parseInt(hex.substring(6, 8), 16);
                return new Vector4f(r / 255f, g / 255f, b / 255f, a / 255f);
            }
        }
        return StandardColors.YELLOW.color;
    }
}
