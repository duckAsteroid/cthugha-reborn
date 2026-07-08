package io.github.duckasteroid.cthugha.animation;

import io.github.duckasteroid.cthugha.params.CompilableValue;
import io.github.duckasteroid.cthugha.params.StringValue;
import io.github.duckasteroid.cthugha.params.UiHint;
import org.codehaus.janino.ClassBodyEvaluator;

/**
 * A {@link StringValue} that Janino-compiles its content into a {@link TimeFunction} each time
 * it is set.  Compilation is synchronous so the result (success or error) is immediately
 * available for the HTTP PATCH response via {@link #getLastCompileError()}.
 *
 * <p>The script content is treated as an expression that is wrapped in:
 * <pre>
 *   public double apply(double t) { return (&lt;expression&gt;); }
 * </pre>
 * {@code import static java.lang.Math.*} is in scope, so {@code sin}, {@code cos}, {@code PI},
 * etc. are available without qualification.</p>
 *
 * <p>On compile failure the previous {@link TimeFunction} continues to run; only
 * {@link #getLastCompileError()} changes, allowing the SPA to report the error without
 * interrupting the animation.</p>
 */
public class ScriptParameter extends StringValue implements CompilableValue {

    private volatile String value;
    private volatile TimeFunction fn;
    private volatile String lastError;

    public ScriptParameter(String name, String defaultExpression) {
        super(name);
        withUiHint(UiHint.CONTROL_TYPE, UiHint.CODE_EDITOR);
        this.value = defaultExpression != null ? defaultExpression : "";
    }

    @Override
    public String getValue() {
        return value;
    }

    /**
     * Stores the expression and compiles it immediately on the calling thread.
     * On success {@link #getFunction()} returns the new {@link TimeFunction} and
     * {@link #getLastCompileError()} returns {@code null}.
     * On failure {@link #getLastCompileError()} returns the error message and the previous
     * function (if any) continues running.
     */
    @Override
    public void setValue(String expression) {
        this.value = expression != null ? expression : "";
        if (!this.value.isBlank()) {
            try {
                ClassBodyEvaluator cbe = new ClassBodyEvaluator();
                cbe.setImplementedInterfaces(new Class<?>[]{ TimeFunction.class });
                cbe.setDefaultImports(new String[]{ "static java.lang.Math.*" });
                cbe.cook("public double apply(double t) { return (" + this.value + "); }");
                fn = (TimeFunction) cbe.getClazz().getDeclaredConstructor().newInstance();
                lastError = null;
            } catch (Exception e) {
                lastError = e.getMessage();
            }
        } else {
            fn = null;
            lastError = null;
        }
        fireChangeListeners();
    }

    /** Returns the compiled function, or {@code null} if blank or last compile failed. */
    public TimeFunction getFunction() {
        return fn;
    }

    @Override
    public String getLastCompileError() {
        return lastError;
    }

    /** (Re-)compiles the current value. No-op if value is blank. */
    void compile() {
        if (!value.isBlank()) {
            setValue(value);
        }
    }
}
