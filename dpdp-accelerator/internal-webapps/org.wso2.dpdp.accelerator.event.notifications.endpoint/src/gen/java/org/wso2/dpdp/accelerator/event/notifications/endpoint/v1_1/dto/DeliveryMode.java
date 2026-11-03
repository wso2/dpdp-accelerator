package org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Exact lowercase value required; case variants, surrounding whitespace, and aliases are not accepted.
 */
public enum DeliveryMode {
  
  WEBHOOK("webhook"),
  
  POLL("poll");

  private String value;

  DeliveryMode(String value) {
    this.value = value;
  }

  @Override
  @JsonValue
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static DeliveryMode fromValue(String value) {
    for (DeliveryMode b : DeliveryMode.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }

}

