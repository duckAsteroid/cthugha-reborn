package io.github.duckasteroid.cthugha.audio;

/**
 * Represents sources for single valued audio data
 */
public enum Channel implements PointValueExtractor {
  /**
   * Extracts the left channel from a (stereo) audio
   */
  LEFT {
    @Override
    public short value(short[] sample) {
      return sample[0];
    }
  },
  /**
   * Extracts the right channel from a (stereo) audio (if available)
   */
  RIGHT {
    @Override
    public short value(short[] sample) {
      if (sample.length > 1) {
        return sample[1];
      } else {
        return sample[0];
      }
    }
  },
  /**
   * Creates an average of the combined channels
   */
  MONO_AVG {
    @Override
    public short value(short[] sample) {
      double sum = 0;
      for (int i = 0; i < sample.length; i++) {
        sum += sample[i];
      }
      return (short) (sum / sample.length);
    }
  },
  /**
   * Creates a difference of the two channels
   */
  MONO_DIFF {
    @Override
    public short value(short[] sample) {
      if (sample.length > 1) {
        return (short) (sample[1] - sample[0]);
      }
      return sample[0];
    }
  };


}
