package org.wso2.dpdp.accelerator.event.notifications.endpoint.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.DeliveryAttempt;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.DeliveryMode;

import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class DeliveryHistory  {
  
  @ApiModelProperty(required = true, value = "")

  private String deliveryId;

  @ApiModelProperty(required = true, value = "")

  private String eventId;

  @ApiModelProperty(value = "")

  private String topic;

  @ApiModelProperty(required = true, value = "")

  private DeliveryMode deliveryMode;

  @ApiModelProperty(required = true, value = "")

  private String currentStatus;

  @ApiModelProperty(required = true, value = "")

  private Long occurredAt;

  @ApiModelProperty(value = "")

  private Long nextRetryAt;

  @ApiModelProperty(value = "")

  private String completionStatus;

  @ApiModelProperty(value = "")

  private String completionEvidence;

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
   * Get topic
   * @return topic
  **/
  @JsonProperty("topic")
  public String getTopic() {
    return topic;
  }

  public void setTopic(String topic) {
    this.topic = topic;
  }

  public DeliveryHistory topic(String topic) {
    this.topic = topic;
    return this;
  }

 /**
   * Get deliveryMode
   * @return deliveryMode
  **/
  @JsonProperty("deliveryMode")
  public DeliveryMode getDeliveryMode() {
    return deliveryMode;
  }

  public void setDeliveryMode(DeliveryMode deliveryMode) {
    this.deliveryMode = deliveryMode;
  }

  public DeliveryHistory deliveryMode(DeliveryMode deliveryMode) {
    this.deliveryMode = deliveryMode;
    return this;
  }

 /**
   * Get currentStatus
   * @return currentStatus
  **/
  @JsonProperty("currentStatus")
  public String getCurrentStatus() {
    return currentStatus;
  }

  public void setCurrentStatus(String currentStatus) {
    this.currentStatus = currentStatus;
  }

  public DeliveryHistory currentStatus(String currentStatus) {
    this.currentStatus = currentStatus;
    return this;
  }

 /**
   * Get occurredAt
   * @return occurredAt
  **/
  @JsonProperty("occurredAt")
  public Long getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(Long occurredAt) {
    this.occurredAt = occurredAt;
  }

  public DeliveryHistory occurredAt(Long occurredAt) {
    this.occurredAt = occurredAt;
    return this;
  }

 /**
   * Get nextRetryAt
   * @return nextRetryAt
  **/
  @JsonProperty("nextRetryAt")
  public Long getNextRetryAt() {
    return nextRetryAt;
  }

  public void setNextRetryAt(Long nextRetryAt) {
    this.nextRetryAt = nextRetryAt;
  }

  public DeliveryHistory nextRetryAt(Long nextRetryAt) {
    this.nextRetryAt = nextRetryAt;
    return this;
  }

 /**
   * Get completionStatus
   * @return completionStatus
  **/
  @JsonProperty("completionStatus")
  public String getCompletionStatus() {
    return completionStatus;
  }

  public void setCompletionStatus(String completionStatus) {
    this.completionStatus = completionStatus;
  }

  public DeliveryHistory completionStatus(String completionStatus) {
    this.completionStatus = completionStatus;
    return this;
  }

 /**
   * Get completionEvidence
   * @return completionEvidence
  **/
  @JsonProperty("completionEvidence")
  public String getCompletionEvidence() {
    return completionEvidence;
  }

  public void setCompletionEvidence(String completionEvidence) {
    this.completionEvidence = completionEvidence;
  }

  public DeliveryHistory completionEvidence(String completionEvidence) {
    this.completionEvidence = completionEvidence;
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
        Objects.equals(this.topic, deliveryHistory.topic) &&
        Objects.equals(this.deliveryMode, deliveryHistory.deliveryMode) &&
        Objects.equals(this.currentStatus, deliveryHistory.currentStatus) &&
        Objects.equals(this.occurredAt, deliveryHistory.occurredAt) &&
        Objects.equals(this.nextRetryAt, deliveryHistory.nextRetryAt) &&
        Objects.equals(this.completionStatus, deliveryHistory.completionStatus) &&
        Objects.equals(this.completionEvidence, deliveryHistory.completionEvidence) &&
        Objects.equals(this.history, deliveryHistory.history);
  }

  @Override
  public int hashCode() {
    return Objects.hash(deliveryId, eventId, topic, deliveryMode, currentStatus, occurredAt, nextRetryAt, completionStatus, completionEvidence, history);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class DeliveryHistory {\n");
    
    sb.append("    deliveryId: ").append(toIndentedString(deliveryId)).append("\n");
    sb.append("    eventId: ").append(toIndentedString(eventId)).append("\n");
    sb.append("    topic: ").append(toIndentedString(topic)).append("\n");
    sb.append("    deliveryMode: ").append(toIndentedString(deliveryMode)).append("\n");
    sb.append("    currentStatus: ").append(toIndentedString(currentStatus)).append("\n");
    sb.append("    occurredAt: ").append(toIndentedString(occurredAt)).append("\n");
    sb.append("    nextRetryAt: ").append(toIndentedString(nextRetryAt)).append("\n");
    sb.append("    completionStatus: ").append(toIndentedString(completionStatus)).append("\n");
    sb.append("    completionEvidence: ").append(toIndentedString(completionEvidence)).append("\n");
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

