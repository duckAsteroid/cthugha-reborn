package io.github.duckasteroid.cthugha.params;

/**
 * A named grouping node with no logic of its own.
 *
 * <p>Use this when you need to wrap a set of child nodes under a label without
 * creating a dedicated class.  Children are added via {@link #addChild(Node)}.</p>
 */
public final class ContainerNode extends AbstractNode {

    public ContainerNode(String name) {
        super(name);
    }
}
