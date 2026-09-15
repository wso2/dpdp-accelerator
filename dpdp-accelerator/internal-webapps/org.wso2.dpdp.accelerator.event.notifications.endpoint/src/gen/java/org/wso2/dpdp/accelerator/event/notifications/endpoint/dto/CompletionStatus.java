package org.wso2.dpdp.accelerator.event.notifications.endpoint.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Gets or Sets CompletionStatus
 */
public enum CompletionStatus {
  
  COMPLETED("completed"),
  
  ACK("ack"),
  
  DISPUTED("disputed"),
  
  PARTIAL("partial");

  private String value;

  CompletionStatus(String value) {
    this.value = value;
  }

  @Override
  @JsonValue
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static CompletionStatus fromValue(String value) {
    for (CompletionStatus b : CompletionStatus.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }

}

