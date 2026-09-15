package org.wso2.dpdp.accelerator.event.notifications.endpoint.dto;

import java.util.HashMap;
import java.util.Map;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Object carried in the JWS payload claim for webhook and poll delivery.
 */
@ApiModel(description="Object carried in the JWS payload claim for webhook and poll delivery.")

public class SignedEventEnvelope  {
  
  @ApiModelProperty(required = true, value = "")

  private String deliveryId;

  @ApiModelProperty(required = true, value = "")

  private String eventId;

  @ApiModelProperty(required = true, value = "")

  private String subscriptionId;

  @ApiModelProperty(required = true, value = "")

  private String orgId;

  @ApiModelProperty(required = true, value = "")

  private String groupId;

  @ApiModelProperty(required = true, value = "")

  private String topic;

  @ApiModelProperty(required = true, value = "")

  private Map<String, Object> eventPayload;
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

  public SignedEventEnvelope deliveryId(String deliveryId) {
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

  public SignedEventEnvelope eventId(String eventId) {
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

  public SignedEventEnvelope subscriptionId(String subscriptionId) {
    this.subscriptionId = subscriptionId;
    return this;
  }

 /**
   * Get orgId
   * @return orgId
  **/
  @JsonProperty("orgId")
  public String getOrgId() {
    return orgId;
  }

  public void setOrgId(String orgId) {
    this.orgId = orgId;
  }

  public SignedEventEnvelope orgId(String orgId) {
    this.orgId = orgId;
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

  public SignedEventEnvelope groupId(String groupId) {
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

  public SignedEventEnvelope topic(String topic) {
    this.topic = topic;
    return this;
  }

 /**
   * Get eventPayload
   * @return eventPayload
  **/
  @JsonProperty("eventPayload")
  public Map<String, Object> getEventPayload() {
    return eventPayload;
  }

  public void setEventPayload(Map<String, Object> eventPayload) {
    this.eventPayload = eventPayload;
  }

  public SignedEventEnvelope eventPayload(Map<String, Object> eventPayload) {
    this.eventPayload = eventPayload;
    return this;
  }

  public SignedEventEnvelope putEventPayloadItem(String key, Object eventPayloadItem) {
    this.eventPayload.put(key, eventPayloadItem);
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
    SignedEventEnvelope signedEventEnvelope = (SignedEventEnvelope) o;
    return Objects.equals(this.deliveryId, signedEventEnvelope.deliveryId) &&
        Objects.equals(this.eventId, signedEventEnvelope.eventId) &&
        Objects.equals(this.subscriptionId, signedEventEnvelope.subscriptionId) &&
        Objects.equals(this.orgId, signedEventEnvelope.orgId) &&
        Objects.equals(this.groupId, signedEventEnvelope.groupId) &&
        Objects.equals(this.topic, signedEventEnvelope.topic) &&
        Objects.equals(this.eventPayload, signedEventEnvelope.eventPayload);
  }

  @Override
  public int hashCode() {
    return Objects.hash(deliveryId, eventId, subscriptionId, orgId, groupId, topic, eventPayload);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class SignedEventEnvelope {\n");
    
    sb.append("    deliveryId: ").append(toIndentedString(deliveryId)).append("\n");
    sb.append("    eventId: ").append(toIndentedString(eventId)).append("\n");
    sb.append("    subscriptionId: ").append(toIndentedString(subscriptionId)).append("\n");
    sb.append("    orgId: ").append(toIndentedString(orgId)).append("\n");
    sb.append("    groupId: ").append(toIndentedString(groupId)).append("\n");
    sb.append("    topic: ").append(toIndentedString(topic)).append("\n");
    sb.append("    eventPayload: ").append(toIndentedString(eventPayload)).append("\n");
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

