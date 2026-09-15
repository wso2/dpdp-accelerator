package org.wso2.dpdp.accelerator.event.notifications.endpoint.dto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class Event  {
  
  @ApiModelProperty(required = true, value = "")

  private String eventId;

  @ApiModelProperty(required = true, value = "")

  private String orgId;

  @ApiModelProperty(required = true, value = "")

  private String groupId;

  @ApiModelProperty(required = true, value = "")

  private String topicId;

  @ApiModelProperty(value = "")

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

  @ApiModelProperty(required = true, value = "")

  private Integer deliveriesCount;
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

  public Event eventId(String eventId) {
    this.eventId = eventId;
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

  public Event orgId(String orgId) {
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

  public Event groupId(String groupId) {
    this.groupId = groupId;
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

  public Event topicId(String topicId) {
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

  public Event topic(String topic) {
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

  public Event payload(String payload) {
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

  public Event purposes(List<String> purposes) {
    this.purposes = purposes;
    return this;
  }

  public Event addPurposesItem(String purposesItem) {
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

  public Event occurredAt(Long occurredAt) {
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

  public Event createdAt(Long createdAt) {
    this.createdAt = createdAt;
    return this;
  }

 /**
   * Get deliveriesCount
   * @return deliveriesCount
  **/
  @JsonProperty("deliveriesCount")
  public Integer getDeliveriesCount() {
    return deliveriesCount;
  }

  public void setDeliveriesCount(Integer deliveriesCount) {
    this.deliveriesCount = deliveriesCount;
  }

  public Event deliveriesCount(Integer deliveriesCount) {
    this.deliveriesCount = deliveriesCount;
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
    Event event = (Event) o;
    return Objects.equals(this.eventId, event.eventId) &&
        Objects.equals(this.orgId, event.orgId) &&
        Objects.equals(this.groupId, event.groupId) &&
        Objects.equals(this.topicId, event.topicId) &&
        Objects.equals(this.topic, event.topic) &&
        Objects.equals(this.payload, event.payload) &&
        Objects.equals(this.purposes, event.purposes) &&
        Objects.equals(this.occurredAt, event.occurredAt) &&
        Objects.equals(this.createdAt, event.createdAt) &&
        Objects.equals(this.deliveriesCount, event.deliveriesCount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(eventId, orgId, groupId, topicId, topic, payload, purposes, occurredAt, createdAt, deliveriesCount);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Event {\n");
    
    sb.append("    eventId: ").append(toIndentedString(eventId)).append("\n");
    sb.append("    orgId: ").append(toIndentedString(orgId)).append("\n");
    sb.append("    groupId: ").append(toIndentedString(groupId)).append("\n");
    sb.append("    topicId: ").append(toIndentedString(topicId)).append("\n");
    sb.append("    topic: ").append(toIndentedString(topic)).append("\n");
    sb.append("    payload: ").append(toIndentedString(payload)).append("\n");
    sb.append("    purposes: ").append(toIndentedString(purposes)).append("\n");
    sb.append("    occurredAt: ").append(toIndentedString(occurredAt)).append("\n");
    sb.append("    createdAt: ").append(toIndentedString(createdAt)).append("\n");
    sb.append("    deliveriesCount: ").append(toIndentedString(deliveriesCount)).append("\n");
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

