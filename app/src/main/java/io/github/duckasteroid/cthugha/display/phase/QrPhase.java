package io.github.duckasteroid.cthugha.display.phase;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.renderaction.RenderActionQueue;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.remote.QrOverlay;

import java.io.IOException;

/**
 * Screen-phase wrapper for {@link QrOverlay}.
 * {@link #show(String)} and {@link #hide()} are thread-safe proxies for the remote
 * server to call when the token rotates or the first client authenticates.
 */
public class QrPhase implements RenderPhase {

    private final QrOverlay qrOverlay;

    public QrPhase(QrOverlay qrOverlay) {
        this.qrOverlay = qrOverlay;
    }

    @Override
    public void init(RenderContext ctx) throws IOException {
        qrOverlay.init(ctx);
    }

    @Override
    public void screenRender(RenderContext ctx) {
        qrOverlay.doRender(ctx);
    }

    @Override
    public void dispose() {
        qrOverlay.dispose();
    }

    @Override
    public void registerActions(ParamNode generalGroup, RenderActionQueue renderActions) {}

    public void show(String url) { qrOverlay.show(url); }
    public void hide()           { qrOverlay.hide(); }
}
