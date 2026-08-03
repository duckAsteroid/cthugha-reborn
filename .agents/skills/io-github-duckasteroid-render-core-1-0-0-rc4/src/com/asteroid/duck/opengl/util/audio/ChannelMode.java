package com.asteroid.duck.opengl.util.audio;

/**
 * Selects which channel(s) of a stereo PCM stream to use when producing a mono output value.
 *
 * <p>Used by {@link RollingAudioBuffer#readSamples} and related methods to control whether the
 * visualiser tracks the left channel, the right channel, or a blended average of both.</p>
 */
public enum ChannelMode {
    /** Use only the left channel sample as the output value. */
    LEFT,
    /** Use only the right channel sample as the output value. */
    RIGHT,
    /**
     * Average the left and right channel samples into a single mono value.
     * Useful when the stereo image does not matter and you want the overall signal level.
     */
    MONO_BLEND
}
