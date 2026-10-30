package org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto.TopicStatus;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of a topic status change such as deregistration.
 */
@ApiModel(description="Result of a topic status change such as deregistration.")

public class TopicStatusUpdate  {
  
  @ApiModelProperty(required = true, value = "")

  private String topicId;

  @ApiModelProperty(required = true, value = "")

  private TopicStatus status;
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

  public TopicStatusUpdate topicId(String topicId) {
    this.topicId = topicId;
    return this;
  }

 /**
   * Get status
   * @return status
  **/
  @JsonProperty("status")
  public TopicStatus getStatus() {
    return status;
  }

  public void setStatus(TopicStatus status) {
    this.status = status;
  }

  public TopicStatusUpdate status(TopicStatus status) {
    this.status = status;
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
    TopicStatusUpdate topicStatusUpdate = (TopicStatusUpdate) o;
    return Objects.equals(this.topicId, topicStatusUpdate.topicId) &&
        Objects.equals(this.status, topicStatusUpdate.status);
  }

  @Override
  public int hashCode() {
    return Objects.hash(topicId, status);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class TopicStatusUpdate {\n");
    
    sb.append("    topicId: ").append(toIndentedString(topicId)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
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

