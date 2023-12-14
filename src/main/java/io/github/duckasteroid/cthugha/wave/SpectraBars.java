package io.github.duckasteroid.cthugha.wave;

import io.github.duckasteroid.cthugha.display.ScreenBuffer;
import io.github.duckasteroid.cthugha.audio.AudioSample;
import io.github.duckasteroid.cthugha.audio.dsp.FastFourierTransform;
import io.github.duckasteroid.cthugha.audio.dsp.FrequencySpectra;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.EnumParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;
import java.awt.Graphics2D;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * This wave draws FFT bars on the screen.
 */
public class SpectraBars implements Wave {
  public BooleanParameter includeDC = new BooleanParameter("Include DC", true);
  public DoubleParameter height = new DoubleParameter("Relative height", 0.0, 1.0, 1.0);
  public DoubleParameter barWidth = new DoubleParameter("Bar widhth", 0, 1, 0.5);

  public EnumParameter<String> drawMode = new EnumParameter<>("Draw mode", List.of("Bar", "Line"));
  public BooleanParameter logarithmic = new BooleanParameter("Logarithmic scale", true);


  public SpectraBars() {
    drawMode.setObjectValue("Line");
  }

  @Override
  public void wave(AudioSample sound, ScreenBuffer buffer) {
    FrequencySpectra spectra = sound.fft();
    Graphics2D graphics = buffer.getGraphics();

    final int start = includeDC.value ? 0 : 1;
    if (spectra != null) {
      graphics.setColor(buffer.getForegroundColor());
      final double max = spectra.getMaxFrequency();
      final int barWidth = (int)(this.barWidth.value * (buffer.width / (spectra.size() - start)));
      int[] xs = new int[spectra.size()];
      int[] ys = new int[spectra.size()];
      for (int i = start; i < spectra.size(); i++) {
        // these are both fractional upper and lower bounds
        double u_freq = spectra.getFrequency(i) / max;
        double l_freq = i == 0 ? 0 : spectra.getFrequency(i - 1) / max;

        double mag = spectra.getMagnitude(i) / 800000;
        final int barHeight =
          (int) (mag * buffer.height * height.value);
        int x = i * barWidth;
        if (drawMode.value == 0) {
          graphics.fillRect(x, 0,
            x + barWidth, barHeight);
        }
        else {
          xs[i] = x + (barWidth / 2);
          ys[i] = barHeight;
        }
      }
      if (drawMode.value == 1) {
        graphics.drawPolyline(xs, ys, xs.length);
      }
    }

    graphics.dispose();
  }

}
