package org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Exact lowercase value required; case variants, surrounding whitespace, and aliases are not accepted.
 */
public enum PurposeFilterMode {
  
  ALL("all"),
  
  ALL_EXCEPT("all_except"),
  
  SPECIFIC("specific");

  private String value;

  PurposeFilterMode(String value) {
    this.value = value;
  }

  @Override
  @JsonValue
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static PurposeFilterMode fromValue(String value) {
    for (PurposeFilterMode b : PurposeFilterMode.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }

}

