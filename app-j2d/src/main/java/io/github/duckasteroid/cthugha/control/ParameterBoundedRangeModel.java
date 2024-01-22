package io.github.duckasteroid.cthugha.control;

import io.github.duckasteroid.cthugha.params.AbstractValue;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import java.util.Arrays;
import javax.swing.BoundedRangeModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;

/**
 * A swing bounded range model for a numeric parameter value
 */
public class ParameterBoundedRangeModel implements BoundedRangeModel {
  private final AbstractValue parameter;
  private boolean adjusting = false;
  private final EventListenerList listeners = new EventListenerList();

  public ParameterBoundedRangeModel(AbstractValue parameter) {
    this.parameter = parameter;
  }

  @Override
  public int getMinimum() {
    return parameter.getMin().intValue();
  }

  @Override
  public void setMinimum(int newMinimum) {
    // ignored
  }

  @Override
  public int getMaximum() {
    return parameter.getMax().intValue();
  }

  @Override
  public void setMaximum(int newMaximum) {
    // ignored
  }

  @Override
  public int getValue() {
    return parameter.getValue().intValue();
  }

  @Override
  public void setValue(int newValue) {
    parameter.setValue(newValue);

    ChangeEvent event = new ChangeEvent(this);
    Arrays.stream(listeners.getListeners(ChangeListener.class))
      .forEach(l -> l.stateChanged(event));

  }

  @Override
  public void setValueIsAdjusting(boolean b) {
    adjusting = b;
  }

  @Override
  public boolean getValueIsAdjusting() {
    return adjusting;
  }

  @Override
  public int getExtent() {
    return 1;
  }

  @Override
  public void setExtent(int newExtent) {
    // ignored
  }

  @Override
  public void setRangeProperties(int value, int extent, int min, int max, boolean adjusting) {
    setValueIsAdjusting(adjusting);
    setValue(value);
  }



  @Override
  public void addChangeListener(ChangeListener x) {
    listeners.add(ChangeListener.class, x);
  }

  @Override
  public void removeChangeListener(ChangeListener x) {
    listeners.remove(ChangeListener.class, x);
  }
}
