package io.github.duckasteroid.cthugha.params;

import static org.junit.jupiter.api.Assertions.*;

import io.github.duckasteroid.cthugha.params.values.EnumParameter;
import org.junit.jupiter.api.Test;

class EnumParameterTest {

  public enum TestEnum {
    ON, KIND_OF_ON, MOSTLY_OFF, OFF
  }
  @Test
  void enumParams() {
    EnumParameter<TestEnum> subj = EnumParameter.forType(TestEnum.class);
    assertEquals(TestEnum.ON, subj.getEnumeration());
    assertEquals(0, subj.getValue());
    subj.setEnumeration(TestEnum.MOSTLY_OFF);
    assertEquals(TestEnum.MOSTLY_OFF, subj.getEnumeration());
    assertEquals(2, subj.getValue());
    System.out.println(subj);
  }
}
