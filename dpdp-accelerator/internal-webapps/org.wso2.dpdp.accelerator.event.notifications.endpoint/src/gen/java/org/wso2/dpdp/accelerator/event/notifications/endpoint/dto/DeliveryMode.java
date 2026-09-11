package org.wso2.dpdp.accelerator.event.notifications.endpoint.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Gets or Sets DeliveryMode
 */
@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = org.wso2.dpdp.accelerator.event.notifications.endpoint.util.RequestEnumDeserializers.Delivery.class)
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

