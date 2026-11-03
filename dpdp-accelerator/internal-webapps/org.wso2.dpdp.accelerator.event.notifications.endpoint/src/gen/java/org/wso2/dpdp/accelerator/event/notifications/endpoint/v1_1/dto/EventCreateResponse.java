package org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class EventCreateResponse  {
  
  @ApiModelProperty(required = true, value = "")

  private String eventId;

  @ApiModelProperty(required = true, value = "")

  private String topicId;

  @ApiModelProperty(required = true, value = "")

  private String topic;

 /**
  * Persisted event JSON serialized as a JSON string.
  */
  @ApiModelProperty(required = true, value = "Persisted event JSON serialized as a JSON string.")

  private String payload;

  @ApiModelProperty(value = "")

  private List<String> purposes;

  @ApiModelProperty(value = "")

  private Long occurredAt;

  @ApiModelProperty(value = "")

  private Long createdAt;
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

  public EventCreateResponse eventId(String eventId) {
    this.eventId = eventId;
    return this;
  }

 /**
   * Get topicId
   * @return topicId
  **/
  @JsonProperty("topicId")
  public String getTopicId() {
    return topicId;
  }

  public void setTopicId(String topicId) {
    this.topicId = topicId;
  }

  public EventCreateResponse topicId(String topicId) {
    this.topicId = topicId;
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

  public EventCreateResponse topic(String topic) {
    this.topic = topic;
    return this;
  }

 /**
   * Persisted event JSON serialized as a JSON string.
   * @return payload
  **/
  @JsonProperty("payload")
  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  public EventCreateResponse payload(String payload) {
    this.payload = payload;
    return this;
  }

 /**
   * Get purposes
   * @return purposes
  **/
  @JsonProperty("purposes")
  public List<String> getPurposes() {
    return purposes;
  }

  public void setPurposes(List<String> purposes) {
    this.purposes = purposes;
  }

  public EventCreateResponse purposes(List<String> purposes) {
    this.purposes = purposes;
    return this;
  }

  public EventCreateResponse addPurposesItem(String purposesItem) {
    this.purposes.add(purposesItem);
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

  public EventCreateResponse occurredAt(Long occurredAt) {
    this.occurredAt = occurredAt;
    return this;
  }

 /**
   * Get createdAt
   * @return createdAt
  **/
  @JsonProperty("createdAt")
  public Long getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Long createdAt) {
    this.createdAt = createdAt;
  }

  public EventCreateResponse createdAt(Long createdAt) {
    this.createdAt = createdAt;
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
    EventCreateResponse eventCreateResponse = (EventCreateResponse) o;
    return Objects.equals(this.eventId, eventCreateResponse.eventId) &&
        Objects.equals(this.topicId, eventCreateResponse.topicId) &&
        Objects.equals(this.topic, eventCreateResponse.topic) &&
        Objects.equals(this.payload, eventCreateResponse.payload) &&
        Objects.equals(this.purposes, eventCreateResponse.purposes) &&
        Objects.equals(this.occurredAt, eventCreateResponse.occurredAt) &&
        Objects.equals(this.createdAt, eventCreateResponse.createdAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(eventId, topicId, topic, payload, purposes, occurredAt, createdAt);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class EventCreateResponse {\n");
    
    sb.append("    eventId: ").append(toIndentedString(eventId)).append("\n");
    sb.append("    topicId: ").append(toIndentedString(topicId)).append("\n");
    sb.append("    topic: ").append(toIndentedString(topic)).append("\n");
    sb.append("    payload: ").append(toIndentedString(payload)).append("\n");
    sb.append("    purposes: ").append(toIndentedString(purposes)).append("\n");
    sb.append("    occurredAt: ").append(toIndentedString(occurredAt)).append("\n");
    sb.append("    createdAt: ").append(toIndentedString(createdAt)).append("\n");
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

