package io.github.duckasteroid.cthugha.params;

import java.util.Random;
import java.util.function.Consumer;

/**
 * Standard implementation of {@link Action}: a named leaf node that runs a lambda body
 * when {@link #execute} is called.
 *
 * <pre>{@code
 * Node save = new AbstractAction("Save", ctx -> {
 *     if (ctx instanceof TabActionContext tctx) {
 *         tctx.tabStore().save(...);
 *     }
 * });
 * }</pre>
 */
public class AbstractAction extends AbstractNode implements Action {

    private final Consumer<ActionContext> body;

    public AbstractAction(String name, Consumer<ActionContext> body) {
        super(name);
        this.body = body;
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.ACTION;
    }

    @Override
    public void execute(ActionContext ctx) {
        body.accept(ctx);
    }

    @Override
    public void randomise(Random rng) {
        // no-op — actions have no randomisable state
    }

    @Override
    public boolean isLeaf() {
        return true;
    }
}
