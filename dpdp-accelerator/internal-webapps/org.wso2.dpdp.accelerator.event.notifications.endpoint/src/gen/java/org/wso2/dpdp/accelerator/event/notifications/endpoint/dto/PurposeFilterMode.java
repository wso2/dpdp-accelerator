package org.wso2.dpdp.accelerator.event.notifications.endpoint.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Gets or Sets PurposeFilterMode
 */
@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = org.wso2.dpdp.accelerator.event.notifications.endpoint.util.RequestEnumDeserializers.FilterMode.class)
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

