package io.github.duckasteroid.cthugha.binding;

import io.github.duckasteroid.cthugha.params.CompilableValue;
import io.github.duckasteroid.cthugha.params.StringValue;
import io.github.duckasteroid.cthugha.params.UiHint;
import org.codehaus.janino.ClassBodyEvaluator;

import java.util.Map;

/**
 * A {@link StringValue} that Janino-compiles its content into a {@link BooleanTimeFunction} each
 * time it is set. Mirrors {@link ScriptParameter}, but for boolean edge-triggered conditions
 * (e.g. {@code "bass() > 0.7"}) instead of numeric expressions.
 *
 * <p>The script content is treated as an expression wrapped in:
 * <pre>
 *   public boolean test() { return (&lt;expression&gt;); }
 * </pre>
 * {@code import static java.lang.Math.*} is in scope, and every helper on {@link ScriptHelpers}
 * ({@code bass()}, {@code random()}, {@code sine(hz)}, {@code state}/{@code global}, etc.) is
 * available.</p>
 *
 * <p>On compile failure the previous {@link BooleanTimeFunction} continues to run; only {@link
 * #getLastCompileError()} changes, matching {@code ScriptParameter}'s graceful-degrade behaviour.</p>
 */
public class ConditionParameter extends StringValue implements CompilableValue {

    private volatile String value;
    private volatile BooleanTimeFunction fn;
    private volatile String lastError;
    private volatile Map<String, Object> localState = Map.of();
    private volatile Map<String, Object> globalState = Map.of();

    public ConditionParameter(String name, String defaultExpression) {
        super(name);
        withUiHint(UiHint.CONTROL_TYPE, UiHint.CODE_EDITOR);
        this.value = defaultExpression != null ? defaultExpression : "";
    }

    /**
     * Wires the script-local and global state maps that every future compiled instance will be
     * bound to (via {@link ScriptHelpers#bindState}). Call once, before the first {@link #compile()}
     * — typically from the owning {@link Binding}'s constructor.
     */
    public void bindState(Map<String, Object> localState, Map<String, Object> globalState) {
        this.localState = localState != null ? localState : Map.of();
        this.globalState = globalState != null ? globalState : Map.of();
    }

    @Override
    public String getValue() {
        return value;
    }

    /**
     * Stores the expression and compiles it immediately on the calling thread.
     * On success {@link #getFunction()} returns the new {@link BooleanTimeFunction} and
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
                cbe.setExtendedClass(ConditionScript.class);
                cbe.setDefaultImports(new String[]{ "static java.lang.Math.*" });
                cbe.cook("public boolean test() { return (" + this.value + "); }");
                ConditionScript compiled = (ConditionScript) cbe.getClazz().getDeclaredConstructor().newInstance();
                compiled.bindState(localState, globalState);
                fn = compiled;
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
    public BooleanTimeFunction getFunction() {
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
