package com.asteroid.duck.opengl.util.audio.analysis;

import java.util.List;

/**
 * A named frequency range used by {@link BeatDetector} to monitor a sub-band of the spectrum.
 *
 * <p>Construct custom bands for any Hz range, or use the built-in presets and
 * {@link #defaults()} for standard bass / snare / hi-hat detection.</p>
 *
 * @param name  human-readable label; used by {@link BeatDetector#getBeatStrength(String)}
 * @param fMin  lower bound of the band in Hz (inclusive)
 * @param fMax  upper bound of the band in Hz (exclusive)
 */
public record FrequencyBand(String name, float fMin, float fMax) {

    /** Kick-drum / sub-bass range: 20–250 Hz. */
    public static final FrequencyBand BASS   = new FrequencyBand("bass",   20f,    250f);

    /** Snare / mid range: 250–2 000 Hz. */
    public static final FrequencyBand SNARE  = new FrequencyBand("snare",  250f,  2_000f);

    /** Hi-hat / high range: 2 000–20 000 Hz. */
    public static final FrequencyBand HI_HAT = new FrequencyBand("hihat", 2_000f, 20_000f);

    /**
     * The three default bands: {@link #BASS}, {@link #SNARE}, {@link #HI_HAT}.
     * Together they span the full audible range (20 Hz – 20 kHz).
     */
    public static List<FrequencyBand> defaults() {
        return List.of(BASS, SNARE, HI_HAT);
    }
}
