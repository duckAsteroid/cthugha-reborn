package com.asteroid.duck.opengl.util.audio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A pluggable, mutable list of {@link AudioDataSource}s — real (see {@link LineAcquirer}) or
 * simulated (see {@code com.asteroid.duck.opengl.util.audio.simulated.SimulatedSources}), in any
 * combination. Carries no notion of a "current" or "selected" source; callers own selection.
 */
public class AudioSources implements Iterable<AudioDataSource> {
    private final List<AudioDataSource> sources = new ArrayList<>();

    public void add(AudioDataSource source) {
        sources.add(source);
    }

    public boolean remove(AudioDataSource source) {
        return sources.remove(source);
    }

    public int size() {
        return sources.size();
    }

    public boolean isEmpty() {
        return sources.isEmpty();
    }

    /**
     * An unmodifiable, live view of the current sources.
     *
     * @return the sources currently registered
     */
    public List<AudioDataSource> list() {
        return Collections.unmodifiableList(sources);
    }

    @Override
    public Iterator<AudioDataSource> iterator() {
        return list().iterator();
    }
}
