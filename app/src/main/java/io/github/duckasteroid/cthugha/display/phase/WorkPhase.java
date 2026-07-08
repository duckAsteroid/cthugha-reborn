package io.github.duckasteroid.cthugha.display.phase;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.color.StandardColors;
import com.asteroid.duck.opengl.util.renderaction.RenderActionQueue;
import com.asteroid.duck.opengl.util.resources.font.FontTexture;
import com.asteroid.duck.opengl.util.resources.font.FontTextureFactory;
import com.asteroid.duck.opengl.util.text.StringRenderer;
import io.github.duckasteroid.cthugha.config.Config;
import io.github.duckasteroid.cthugha.params.ContainerNode;
import io.github.duckasteroid.cthugha.work.BackgroundWorkQueue;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.awt.Font;
import java.awt.Rectangle;
import java.io.IOException;

import static org.lwjgl.opengl.GL11.*;

/**
 * Screen overlay that shows a spinning indicator and status label while background work is active.
 * Separate from {@link NotifPhase} — the spinner persists for the duration of the work,
 * whereas notifications are transient timed messages.
 *
 * <p>Configurable in the {@code [Work]} INI section:
 * {@code font}, {@code size}, {@code style}, {@code color},
 * {@code location}, {@code padding}, {@code rpm}.
 */
public class WorkPhase implements RenderPhase {

    private static final String SECTION = "Work";
    private static final Config CFG = Config.singleton();

    private final BackgroundWorkQueue workQueue;

    // Icon: "—" rotated continuously around its own centre
    private StringRenderer iconRenderer;
    private float iconCx, iconCy;
    private float iconHalfW, iconHalfH;

    // Label: status text to the right of the icon
    private StringRenderer labelRenderer;

    private double rpm;

    public WorkPhase(BackgroundWorkQueue workQueue) {
        this.workQueue = workQueue;
    }

    @Override
    public void init(RenderContext ctx) throws IOException {
        Rectangle win = ctx.getWindow();

        String fontName = CFG.getConfig(SECTION, "font",  "Monospaced");
        String sizeStr  = CFG.getConfig(SECTION, "size",  "18");
        String styleStr = CFG.getConfig(SECTION, "style", "BOLD");
        int size  = PhaseConfig.parseFontSize(sizeStr, win.height);
        int style = PhaseConfig.parseFontStyle(styleStr);
        Vector4f color = PhaseConfig.parseColor(CFG.getConfig(SECTION, "color", "CYAN"), StandardColors.CYAN.color);

        String location = CFG.getConfig(SECTION, "location", "BOTTOM_LEFT").trim().toUpperCase();
        int padding = PhaseConfig.parseDimension(CFG.getConfig(SECTION, "padding", "1%"), win.height);
        rpm = CFG.getConfigAs(SECTION, "rpm", "60", Double::parseDouble);

        FontTexture font = new FontTextureFactory(new Font(fontName, style, size), true).createFontTexture();

        iconRenderer = new StringRenderer(font);
        iconRenderer.init(ctx);
        iconRenderer.setText("—");
        iconRenderer.setTextColor(color);

        float iconW = font.getWidth("—");
        float iconH = font.getFontHeight();
        iconHalfW = iconW / 2f;
        iconHalfH = iconH / 2f;

        boolean right  = location.contains("RIGHT");
        boolean bottom = location.contains("BOTTOM");
        float baseX = right  ? win.width  - padding - iconW : padding;
        float baseY = bottom ? win.height - padding          : padding + iconH;

        iconCx = baseX + iconHalfW;
        iconCy = baseY + iconHalfH;

        labelRenderer = new StringRenderer(font);
        labelRenderer.init(ctx);
        labelRenderer.setTransform(new Matrix4f().translate(baseX + iconW + 8f, bottom ? baseY - iconHalfH : baseY - iconH, 0f));
        labelRenderer.setTextColor(color);
    }

    @Override
    public void screenRender(RenderContext ctx) {
        if (!workQueue.isAnyActive()) return;

        float angle = (float)(ctx.getClock().elapsed() * rpm / 60.0 * Math.PI * 2.0);
        iconRenderer.setTransform(new Matrix4f()
                .translate(iconCx, iconCy, 0f)
                .rotateZ(angle)
                .translate(-iconHalfW, -iconHalfH, 0f));

        labelRenderer.setText(workQueue.activeStatus().orElse("Working…"));

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        iconRenderer.doRender(ctx);
        labelRenderer.doRender(ctx);
        glDisable(GL_BLEND);
    }

    @Override
    public void dispose() {
        if (iconRenderer  != null) iconRenderer.dispose();
        if (labelRenderer != null) labelRenderer.dispose();
    }

    @Override
    public void registerActions(ContainerNode generalGroup, RenderActionQueue renderActions) {}
}
