package io.github.duckasteroid.cthugha.params;

/**
 * A {@link StringValue} whose content is compiled when set.
 *
 * <p>The {@link io.github.duckasteroid.cthugha.remote.RemoteServer} PATCH handler checks for
 * this interface after calling {@link StringValue#setValue} and includes
 * {@link #getLastCompileError()} in the response when non-null, so the SPA can surface
 * compile errors inline without a separate SSE channel.</p>
 */
public interface CompilableValue {

    /**
     * Returns the error message from the most recent failed compilation, or {@code null} if
     * the last {@code setValue} call compiled successfully (or no compile has occurred yet).
     */
    String getLastCompileError();
}
