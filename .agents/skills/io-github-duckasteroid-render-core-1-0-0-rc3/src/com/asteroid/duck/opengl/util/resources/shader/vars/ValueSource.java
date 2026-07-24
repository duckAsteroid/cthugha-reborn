package com.asteroid.duck.opengl.util.resources.shader.vars;

import java.util.function.Supplier;

public interface ValueSource<T> extends Supplier<T> {
    enum ChangeStatus {
        UNCHANGED,
        CHANGED,
        UNKNOWN
    }

    default ChangeStatus changeStatus() {
        return ChangeStatus.UNKNOWN;
    }
}
