package org.wso2.dpdp.accelerator.event.notifications.endpoint.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Gets or Sets SubscriptionStatus
 */
@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = org.wso2.dpdp.accelerator.event.notifications.endpoint.util.RequestEnumDeserializers.Status.class)
public enum SubscriptionStatus {
  
  ACTIVE("active"),
  
  PENDING("pending"),
  
  STALE("stale"),
  
  DELETED("deleted");

  private String value;

  SubscriptionStatus(String value) {
    this.value = value;
  }

  @Override
  @JsonValue
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static SubscriptionStatus fromValue(String value) {
    for (SubscriptionStatus b : SubscriptionStatus.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }

}

