package io.github.duckasteroid.cthugha.display.phase;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.color.StandardColors;
import com.asteroid.duck.opengl.util.resources.font.FontTexture;
import com.asteroid.duck.opengl.util.resources.font.FontTextureFactory;
import com.asteroid.duck.opengl.util.renderaction.RenderActionQueue;
import com.asteroid.duck.opengl.util.text.StringRenderer;
import io.github.duckasteroid.cthugha.JCthugha;
import io.github.duckasteroid.cthugha.config.Config;
import io.github.duckasteroid.cthugha.params.ParamNode;
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
 * {@code font}, {@code size}, {@code style}, {@code color},
 * {@code location}, {@code padding}, {@code duration}.
 */
public class NotifPhase implements RenderPhase {

    private static final String SECTION = "Notifications";
    private static final Config CFG = Config.singleton();

    private final JCthugha cthugha;
    private StringRenderer notifRenderer;
    private Instant notifExpiry = null;
    private Duration notifDuration;

    /** One of: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT */
    private String location;
    /** Padding in pixels from the nearest edge(s). */
    private int paddingPx;

    public NotifPhase(JCthugha cthugha) {
        this.cthugha = cthugha;
    }

    @Override
    public void init(RenderContext ctx) throws IOException {
        Rectangle win = ctx.getWindow();

        String fontName = CFG.getConfig(SECTION, "font",  "Monospaced");
        String sizeStr  = CFG.getConfig(SECTION, "size",  "18");
        String styleStr = CFG.getConfig(SECTION, "style", "BOLD");
        int size  = PhaseConfig.parseFontSize(sizeStr, win.height);
        int style = PhaseConfig.parseFontStyle(styleStr);

        location  = CFG.getConfig(SECTION, "location", "TOP_LEFT").trim().toUpperCase();
        paddingPx = PhaseConfig.parseDimension(CFG.getConfig(SECTION, "padding", "1%"), win.height);

        Vector4f color = PhaseConfig.parseColor(CFG.getConfig(SECTION, "color", "YELLOW"), StandardColors.YELLOW.color);

        notifDuration = Duration.ofSeconds(
                CFG.getConfigAs(SECTION, "duration", "3", Integer::parseInt));

        FontTexture font = new FontTextureFactory(new Font(fontName, style, size), true).createFontTexture();
        notifRenderer = new StringRenderer(font);
        notifRenderer.init(ctx);
        notifRenderer.setTextColor(color);
    }

    @Override
    public void screenRender(RenderContext ctx) {
        String notif = cthugha.pollNotification();
        if (notif != null) {
            notifRenderer.setText(notif);
            notifExpiry = Instant.now().plus(notifDuration);
            updateTransform(notif, ctx.getWindow());
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
    public void registerActions(ParamNode generalGroup, RenderActionQueue renderActions) {}

    /**
     * Recomputes the text transform based on location + padding.
     * y uses baseline coordinates: TOP adds fontHeight so the ascent clears the padding margin;
     * BOTTOM subtracts padding so the descent sits just inside the edge.
     */
    private void updateTransform(String text, Rectangle win) {
        FontTexture font = notifRenderer.getFontTexture();
        int fontHeight   = font.getFontHeight();
        float textWidth  = font.getWidth(text);

        boolean right  = location.contains("RIGHT");
        boolean bottom = location.contains("BOTTOM");

        float x = right  ? win.width  - paddingPx - textWidth : paddingPx;
        float y = bottom ? win.height - paddingPx              : paddingPx + fontHeight;

        notifRenderer.setTransform(new Matrix4f().translate(x, y, 0.0f));
    }

}
