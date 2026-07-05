package io.github.duckasteroid.cthugha.display.phase;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.color.StandardColors;
import com.asteroid.duck.opengl.util.resources.font.FontTexture;
import com.asteroid.duck.opengl.util.resources.font.FontTextureFactory;
import com.asteroid.duck.opengl.util.renderaction.RenderActionQueue;
import com.asteroid.duck.opengl.util.text.StringRenderer;
import io.github.duckasteroid.cthugha.JCthugha;
import io.github.duckasteroid.cthugha.params.ContainerNode;
import org.joml.Matrix4f;

import java.awt.Font;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import static org.lwjgl.opengl.GL11.*;

/**
 * Renders transient notification messages as an RGBA screen overlay.
 * Notifications are polled from {@link JCthugha#pollNotification()} each frame and
 * displayed for {@value #NOTIF_SECONDS} seconds before fading out.
 */
public class NotifPhase implements RenderPhase {

    private static final int NOTIF_SECONDS = 3;
    private static final Duration NOTIF_DURATION = Duration.ofSeconds(NOTIF_SECONDS);

    private final JCthugha cthugha;
    private StringRenderer notifRenderer;
    private Instant notifExpiry = null;

    public NotifPhase(JCthugha cthugha) {
        this.cthugha = cthugha;
    }

    @Override
    public void init(RenderContext ctx) throws IOException {
        FontTexture font = new FontTextureFactory(
                new Font("Monospaced", Font.BOLD, 18), true).createFontTexture();
        notifRenderer = new StringRenderer(font);
        notifRenderer.init(ctx);
        notifRenderer.setTransform(new Matrix4f().translate(20.0f, 30.0f, 0.0f));
        notifRenderer.setTextColor(StandardColors.YELLOW.color);
    }

    @Override
    public void screenRender(RenderContext ctx) {
        String notif = cthugha.pollNotification();
        if (notif != null) {
            notifRenderer.setText(notif);
            notifExpiry = Instant.now().plus(NOTIF_DURATION);
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
}
