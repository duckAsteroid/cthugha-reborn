package io.github.duckasteroid.cthugha.audio;

public enum Channel implements PointValueExtractor {
  LEFT {
    @Override
    public short value(short[] sample) {
      return sample[0];
    }
  },
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
