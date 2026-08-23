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

  @Test
  void selectedLabelTracksTheSelectedOption() {
    EnumParameter<TestEnum> subj = EnumParameter.forType(TestEnum.class);
    assertEquals("ON", subj.getSelectedLabel());
    subj.setEnumeration(TestEnum.MOSTLY_OFF);
    assertEquals("MOSTLY_OFF", subj.getSelectedLabel());
  }

  @Test
  void selectByLabelFindsTheMatchingOptionRegardlessOfIndex() {
    EnumParameter<TestEnum> subj = EnumParameter.forType(TestEnum.class);
    assertTrue(subj.selectByLabel("MOSTLY_OFF"));
    assertEquals(TestEnum.MOSTLY_OFF, subj.getEnumeration());
  }

  @Test
  void selectByLabelLeavesSelectionUnchangedWhenLabelIsUnknown() {
    EnumParameter<TestEnum> subj = EnumParameter.forType(TestEnum.class);
    subj.setEnumeration(TestEnum.KIND_OF_ON);
    assertFalse(subj.selectByLabel("DOES_NOT_EXIST"));
    assertEquals(TestEnum.KIND_OF_ON, subj.getEnumeration());
  }
}
