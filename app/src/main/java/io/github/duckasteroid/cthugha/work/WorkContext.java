package io.github.duckasteroid.cthugha.work;

import com.asteroid.duck.opengl.util.RenderContext;
import java.util.function.Consumer;

public interface WorkContext {
    /** Update the status label shown in the work spinner. */
    void setStatus(String message);

    /** Schedule an action to run on the GL thread at the start of the next frame. */
    void enqueueRenderAction(Consumer<RenderContext> action);
}
