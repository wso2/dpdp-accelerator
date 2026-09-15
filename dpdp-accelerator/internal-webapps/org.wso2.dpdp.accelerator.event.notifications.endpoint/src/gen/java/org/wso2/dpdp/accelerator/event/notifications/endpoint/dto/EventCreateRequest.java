package org.wso2.dpdp.accelerator.event.notifications.endpoint.dto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class EventCreateRequest  {
  
  @ApiModelProperty(required = true, value = "")

  private String topic;

  @ApiModelProperty(value = "")

  private List<String> purposes;

  @ApiModelProperty(required = true, value = "")

  private Map<String, Object> payload;
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

  public EventCreateRequest topic(String topic) {
    this.topic = topic;
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

  public EventCreateRequest purposes(List<String> purposes) {
    this.purposes = purposes;
    return this;
  }

  public EventCreateRequest addPurposesItem(String purposesItem) {
    this.purposes.add(purposesItem);
    return this;
  }

 /**
   * Get payload
   * @return payload
  **/
  @JsonProperty("payload")
  public Map<String, Object> getPayload() {
    return payload;
  }

  public void setPayload(Map<String, Object> payload) {
    this.payload = payload;
  }

  public EventCreateRequest payload(Map<String, Object> payload) {
    this.payload = payload;
    return this;
  }

  public EventCreateRequest putPayloadItem(String key, Object payloadItem) {
    this.payload.put(key, payloadItem);
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
    EventCreateRequest eventCreateRequest = (EventCreateRequest) o;
    return Objects.equals(this.topic, eventCreateRequest.topic) &&
        Objects.equals(this.purposes, eventCreateRequest.purposes) &&
        Objects.equals(this.payload, eventCreateRequest.payload);
  }

  @Override
  public int hashCode() {
    return Objects.hash(topic, purposes, payload);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class EventCreateRequest {\n");
    
    sb.append("    topic: ").append(toIndentedString(topic)).append("\n");
    sb.append("    purposes: ").append(toIndentedString(purposes)).append("\n");
    sb.append("    payload: ").append(toIndentedString(payload)).append("\n");
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

