package io.github.duckasteroid.cthugha.display;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RenderSizeTest {

    @Test
    void defaultFitsTheOutputsShapeWithin1080p() {
        assertEquals(new Dimension(1920, 1080), RenderSize.compute(3840, 2160, null, null, null));
        assertEquals(new Dimension(1920, 960), RenderSize.compute(2000, 1000, null, null, null));
        assertEquals(new Dimension(1440, 1080), RenderSize.compute(2880, 2160, null, null, null));
    }

    @Test
    void outputsThatAlreadyFitAreUsedOneToOne() {
        assertEquals(new Dimension(1280, 720), RenderSize.compute(1280, 720, null, null, null));
        assertEquals(new Dimension(1920, 1080), RenderSize.compute(1920, 1080, null, null, null));
    }

    @Test
    void renderMaxChangesOrRemovesTheCap() {
        assertEquals(new Dimension(1280, 720), RenderSize.compute(3840, 2160, null, null, "1280x720"));
        assertEquals(new Dimension(3840, 2160), RenderSize.compute(3840, 2160, null, null, "0"));
    }

    @Test
    void explicitSizesOverrideTheCap() {
        assertEquals(new Dimension(800, 600), RenderSize.compute(3840, 2160, "800", "600px", null));
        // Percentages follow the current output, not the window size at startup.
        assertEquals(new Dimension(1920, 1080), RenderSize.compute(3840, 2160, "50%", "50%", null));
        assertEquals(new Dimension(1000, 500), RenderSize.compute(2000, 1000, "50%", "50%", null));
        // An unset axis is 100% of the output.
        assertEquals(new Dimension(640, 2160), RenderSize.compute(3840, 2160, "640", null, null));
    }

    @Test
    void parseMax() {
        assertEquals(new Dimension(1920, 1080), RenderSize.parseMax("1920x1080"));
        assertEquals(new Dimension(1280, 720), RenderSize.parseMax(" 1280 X 720 "));
        assertNull(RenderSize.parseMax("0"));
    }
}
