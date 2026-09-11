package org.wso2.dpdp.accelerator.event.notifications.endpoint.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.DeliveryMode;

import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class Delivery  {
  
  @ApiModelProperty(required = true, value = "")

  private String deliveryId;

  @ApiModelProperty(required = true, value = "")

  private String eventId;

  @ApiModelProperty(value = "")

  private String subscriptionId;

  @ApiModelProperty(value = "")

  private String groupId;

  @ApiModelProperty(value = "")

  private String topic;

  @ApiModelProperty(required = true, value = "")

  private String currentStatus;

  @ApiModelProperty(required = true, value = "")

  private DeliveryMode deliveryMode;

  @ApiModelProperty(required = true, value = "")

  private Long occurredAt;
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

  public Delivery deliveryId(String deliveryId) {
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

  public Delivery eventId(String eventId) {
    this.eventId = eventId;
    return this;
  }

 /**
   * Get subscriptionId
   * @return subscriptionId
  **/
  @JsonProperty("subscriptionId")
  public String getSubscriptionId() {
    return subscriptionId;
  }

  public void setSubscriptionId(String subscriptionId) {
    this.subscriptionId = subscriptionId;
  }

  public Delivery subscriptionId(String subscriptionId) {
    this.subscriptionId = subscriptionId;
    return this;
  }

 /**
   * Get groupId
   * @return groupId
  **/
  @JsonProperty("groupId")
  public String getGroupId() {
    return groupId;
  }

  public void setGroupId(String groupId) {
    this.groupId = groupId;
  }

  public Delivery groupId(String groupId) {
    this.groupId = groupId;
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

  public Delivery topic(String topic) {
    this.topic = topic;
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

  public Delivery currentStatus(String currentStatus) {
    this.currentStatus = currentStatus;
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

  public Delivery deliveryMode(DeliveryMode deliveryMode) {
    this.deliveryMode = deliveryMode;
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

  public Delivery occurredAt(Long occurredAt) {
    this.occurredAt = occurredAt;
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
    Delivery delivery = (Delivery) o;
    return Objects.equals(this.deliveryId, delivery.deliveryId) &&
        Objects.equals(this.eventId, delivery.eventId) &&
        Objects.equals(this.subscriptionId, delivery.subscriptionId) &&
        Objects.equals(this.groupId, delivery.groupId) &&
        Objects.equals(this.topic, delivery.topic) &&
        Objects.equals(this.currentStatus, delivery.currentStatus) &&
        Objects.equals(this.deliveryMode, delivery.deliveryMode) &&
        Objects.equals(this.occurredAt, delivery.occurredAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(deliveryId, eventId, subscriptionId, groupId, topic, currentStatus, deliveryMode, occurredAt);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Delivery {\n");
    
    sb.append("    deliveryId: ").append(toIndentedString(deliveryId)).append("\n");
    sb.append("    eventId: ").append(toIndentedString(eventId)).append("\n");
    sb.append("    subscriptionId: ").append(toIndentedString(subscriptionId)).append("\n");
    sb.append("    groupId: ").append(toIndentedString(groupId)).append("\n");
    sb.append("    topic: ").append(toIndentedString(topic)).append("\n");
    sb.append("    currentStatus: ").append(toIndentedString(currentStatus)).append("\n");
    sb.append("    deliveryMode: ").append(toIndentedString(deliveryMode)).append("\n");
    sb.append("    occurredAt: ").append(toIndentedString(occurredAt)).append("\n");
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

