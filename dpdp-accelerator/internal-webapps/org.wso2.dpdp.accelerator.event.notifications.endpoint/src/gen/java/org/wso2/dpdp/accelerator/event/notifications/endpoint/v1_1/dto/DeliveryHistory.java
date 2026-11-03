package org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto.DeliveryAttempt;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Attempt history for a delivery
 */
@ApiModel(description="Attempt history for a delivery")

public class DeliveryHistory  {
  
  @ApiModelProperty(required = true, value = "")

  private String deliveryId;

  @ApiModelProperty(value = "")

  private String eventId;

  @ApiModelProperty(required = true, value = "")

  private List<DeliveryAttempt> history;
 /**
   * Get deliveryId
   * @return deliveryId
  **/
  @JsonProperty("deliveryId")
  public String getDeliveryId() {
    return deliveryId;
  }

  public void setDeliveryId(String deliveryId) {
    this.deliveryId = deliveryId;
  }

  public DeliveryHistory deliveryId(String deliveryId) {
    this.deliveryId = deliveryId;
    return this;
  }

 /**
   * Get eventId
   * @return eventId
  **/
  @JsonProperty("eventId")
  public String getEventId() {
    return eventId;
  }

  public void setEventId(String eventId) {
    this.eventId = eventId;
  }

  public DeliveryHistory eventId(String eventId) {
    this.eventId = eventId;
    return this;
  }

 /**
   * Get history
   * @return history
  **/
  @JsonProperty("history")
  public List<DeliveryAttempt> getHistory() {
    return history;
  }

  public void setHistory(List<DeliveryAttempt> history) {
    this.history = history;
  }

  public DeliveryHistory history(List<DeliveryAttempt> history) {
    this.history = history;
    return this;
  }

  public DeliveryHistory addHistoryItem(DeliveryAttempt historyItem) {
    this.history.add(historyItem);
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DeliveryHistory deliveryHistory = (DeliveryHistory) o;
    return Objects.equals(this.deliveryId, deliveryHistory.deliveryId) &&
        Objects.equals(this.eventId, deliveryHistory.eventId) &&
        Objects.equals(this.history, deliveryHistory.history);
  }

  @Override
  public int hashCode() {
    return Objects.hash(deliveryId, eventId, history);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class DeliveryHistory {\n");
    
    sb.append("    deliveryId: ").append(toIndentedString(deliveryId)).append("\n");
    sb.append("    eventId: ").append(toIndentedString(eventId)).append("\n");
    sb.append("    history: ").append(toIndentedString(history)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private static String toIndentedString(Object o) {
    return o == null ? "null" : o.toString().replace("\n", "\n    ");
  }
}

