package org.wso2.dpdp.accelerator.event.notifications.endpoint.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Gets or Sets TopicStatus
 */
public enum TopicStatus {
  
  ACTIVE("active"),
  
  DELETED("deleted");

  private String value;

  TopicStatus(String value) {
    this.value = value;
  }

  @Override
  @JsonValue
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static TopicStatus fromValue(String value) {
    for (TopicStatus b : TopicStatus.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    if ("deregistered".equals(value)) {
      return DELETED;
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }

}

