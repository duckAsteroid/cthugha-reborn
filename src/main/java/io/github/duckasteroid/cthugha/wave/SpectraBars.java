package io.github.duckasteroid.cthugha.wave;

import io.github.duckasteroid.cthugha.ScreenBuffer;
import io.github.duckasteroid.cthugha.audio.AudioSample;
import io.github.duckasteroid.cthugha.audio.dsp.FastFourierTransform;
import io.github.duckasteroid.cthugha.audio.dsp.FrequencySpectra;
import java.awt.Graphics2D;
import java.time.Duration;
import java.time.Instant;

public class SpectraBars implements Wave {
  private boolean includeDC = false;
  private double height = 1.0;
  private final FastFourierTransform transform;

  private int steps = 500;

  private Duration dwellTime = Duration.ofSeconds(1);

  private FrequencySpectra peakSpectra;


  public SpectraBars(FastFourierTransform transform) {
    this.transform = transform;
  }

  @Override
  public void wave(AudioSample sound, ScreenBuffer buffer) {
    FrequencySpectra spectra = transform.transform(sound);
    Graphics2D graphics = buffer.getGraphics();
    graphics.setColor(buffer.getForegroundColor());
    final int start = includeDC ? 0 : 1;

    if (peakSpectra != null) {
      // draw it
      final int barWidth = buffer.width / (peakSpectra.size() - start);
      for (int i = start; i < peakSpectra.size(); i++) {
        final int barHeight =
          (int) (peakSpectra.getMagnitude(i) * buffer.height * height);
        graphics.fillRect(i * barWidth, Math.max(barHeight - 10, 0), barWidth, barHeight);
      }
      // scale it (for next time)
      peakSpectra = peakSpectra.subtract(1000);
    }

    if (spectra != null) {
      final int barWidth = buffer.width / (spectra.size() - start);
      for (int i = start; i < spectra.size(); i++) {
        final int barHeight =
          (int) (spectra.getNormalisedMagnitude(i) * buffer.height * height);
        graphics.fillRect(i * barWidth, 0, barWidth, barHeight);
      }
      if (peakSpectra == null) {
        peakSpectra = spectra;
      }
      else {
        peakSpectra = peakSpectra.combineMaxima(spectra);
      }
    }

    graphics.dispose();
  }
}
