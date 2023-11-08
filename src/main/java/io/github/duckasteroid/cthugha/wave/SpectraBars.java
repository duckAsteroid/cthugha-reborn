package io.github.duckasteroid.cthugha.wave;

import io.github.duckasteroid.cthugha.display.ScreenBuffer;
import io.github.duckasteroid.cthugha.audio.AudioSample;
import io.github.duckasteroid.cthugha.audio.dsp.FastFourierTransform;
import io.github.duckasteroid.cthugha.audio.dsp.FrequencySpectra;
import java.awt.Graphics2D;
import java.time.Duration;
import java.util.Optional;

/**
 * This wave draws FFT bars on the screen.
 */
public class SpectraBars implements Wave {
  private boolean includeDC = false;
  private double height = 1.0;

  private Optional<Double> logScale = Optional.empty(); //Optional.of(Math.E);
  private final FastFourierTransform transform;

  private Duration dwellTime = Duration.ofSeconds(1);

  public SpectraBars(FastFourierTransform transform) {
    this.transform = transform;
  }

  public SpectraBars logScale(Optional<Double> logScale) {
    this.logScale = logScale;
    return this;
  }

  @Override
  public void wave(AudioSample sound, ScreenBuffer buffer) {
    FrequencySpectra spectra = transform.transform(sound);
    Graphics2D graphics = buffer.getGraphics();

    final int start = includeDC ? 0 : 1;
    if (spectra != null) {
      graphics.setColor(buffer.getForegroundColor());
      final double max = logify(spectra.getMaxFrequency());
      //final int barWidth = buffer.width / (spectra.size() - start);
      for (int i = start; i < spectra.size(); i++) {
        double u_freq = logify(spectra.getFrequency(i)) / max;
        double l_freq = i == 0 ? 0 : logify(spectra.getFrequency(i - 1)) / max;

        double mag = spectra.getNormalisedMagnitude(i);
        final int barHeight =
          (int) (mag * buffer.height * height);

        graphics.fillRect((int)(l_freq * buffer.width), 0,
          (int)(u_freq * buffer.width) , barHeight);
      }
    }

    graphics.dispose();
  }

  private double logify(double x) {
    return logScale
      .map(log -> Math.log(x))
      .orElse(x);
  }

}
