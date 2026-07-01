package io.github.duckasteroid.cthugha.params.values;

import io.github.duckasteroid.cthugha.params.StringValue;

/** Concrete mutable string parameter. */
public class StringParameter extends StringValue {

    private volatile String value;

    public StringParameter(String name, String defaultValue) {
        super(name);
        this.value = defaultValue != null ? defaultValue : "";
    }

    @Override
    public String getValue() { return value; }

    @Override
    public void setValue(String value) {
        this.value = value != null ? value : "";
        fireChangeListeners();
    }
}
