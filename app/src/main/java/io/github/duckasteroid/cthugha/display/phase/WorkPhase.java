package io.github.duckasteroid.cthugha.display.phase;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.color.StandardColors;
import com.asteroid.duck.opengl.util.renderaction.RenderActionQueue;
import com.asteroid.duck.opengl.util.resources.font.FontTexture;
import com.asteroid.duck.opengl.util.resources.font.FontTextureFactory;
import com.asteroid.duck.opengl.util.text.StringRenderer;
import io.github.duckasteroid.cthugha.params.ContainerNode;
import io.github.duckasteroid.cthugha.work.BackgroundWorkQueue;
import org.joml.Matrix4f;

import java.awt.Font;
import java.io.IOException;

import static org.lwjgl.opengl.GL11.*;

/**
 * Screen overlay that shows a spinning indicator and status label while background work is active.
 * Separate from {@link NotifPhase} — the spinner persists for the duration of the work,
 * whereas notifications are transient timed messages.
 */
public class WorkPhase implements RenderPhase {

    private static final double SPINNER_RPM = 60.0;

    private final BackgroundWorkQueue workQueue;

    // Icon: "—" rotated continuously around its own centre
    private StringRenderer iconRenderer;
    private float iconCx, iconCy;     // centre of rotation in screen coords
    private float iconHalfW, iconHalfH;

    // Label: status text to the right of the icon
    private StringRenderer labelRenderer;

    public WorkPhase(BackgroundWorkQueue workQueue) {
        this.workQueue = workQueue;
    }

    @Override
    public void init(RenderContext ctx) throws IOException {
        FontTexture font = new FontTextureFactory(
                new Font("Monospaced", Font.BOLD, 18), true).createFontTexture();

        // Icon renderer — text is "—"; transform rebuilt each frame with rotation
        iconRenderer = new StringRenderer(font);
        iconRenderer.init(ctx);
        iconRenderer.setText("—");
        iconRenderer.setTextColor(StandardColors.CYAN.color);

        float iconW = font.getWidth("—");
        float iconH = font.getFontHeight();
        iconHalfW = iconW / 2f;
        iconHalfH = iconH / 2f;

        java.awt.Rectangle win = ctx.getWindow();
        float baseX = 20f;
        float baseY = win.height - 30f;

        // Centre of the icon in screen space
        iconCx = baseX + iconHalfW;
        iconCy = baseY + iconHalfH;

        // Label sits 8px to the right of the icon bounding box
        labelRenderer = new StringRenderer(font);
        labelRenderer.init(ctx);
        labelRenderer.setTransform(new Matrix4f().translate(baseX + iconW + 8f, baseY, 0f));
        labelRenderer.setTextColor(StandardColors.CYAN.color);
    }

    @Override
    public void screenRender(RenderContext ctx) {
        if (!workQueue.isAnyActive()) return;

        // Smooth continuous rotation: translate to centre, rotate, translate back to origin
        float angle = (float)(ctx.getClock().elapsed() * SPINNER_RPM / 60.0 * Math.PI * 2.0);
        iconRenderer.setTransform(new Matrix4f()
                .translate(iconCx, iconCy, 0f)
                .rotateZ(angle)
                .translate(-iconHalfW, -iconHalfH, 0f));

        String label = workQueue.activeStatus().orElse("Working…");
        labelRenderer.setText(label);

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
