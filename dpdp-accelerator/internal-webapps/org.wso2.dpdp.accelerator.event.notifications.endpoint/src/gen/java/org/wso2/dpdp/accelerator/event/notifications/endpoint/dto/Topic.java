package org.wso2.dpdp.accelerator.event.notifications.endpoint.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.TopicStatus;

import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class Topic  {
  
  @ApiModelProperty(required = true, value = "")

  private String name;

  @ApiModelProperty(value = "")

  private String description;

  @ApiModelProperty(required = true, value = "")

  private String topicId;

  @ApiModelProperty(required = true, value = "")

  private TopicStatus status;

public enum InitiatedByEnum {

SYSTEM(String.valueOf("system")), USER(String.valueOf("user"));


    private String value;

    InitiatedByEnum (String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    @Override
    @JsonValue
    public String toString() {
        return String.valueOf(value);
    }

    @JsonCreator
    public static InitiatedByEnum fromValue(String value) {
        for (InitiatedByEnum b : InitiatedByEnum.values()) {
            if (b.value.equals(value)) {
                return b;
            }
        }
        throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
}

  @ApiModelProperty(required = true, value = "")

  private InitiatedByEnum initiatedBy;
 /**
   * Get name
   * @return name
  **/
  @JsonProperty("name")
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Topic name(String name) {
    this.name = name;
    return this;
  }

 /**
   * Get description
   * @return description
  **/
  @JsonProperty("description")
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Topic description(String description) {
    this.description = description;
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

  public Topic topicId(String topicId) {
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

  public Topic status(TopicStatus status) {
    this.status = status;
    return this;
  }

 /**
   * Get initiatedBy
   * @return initiatedBy
  **/
  @JsonProperty("initiatedBy")
  public String getInitiatedBy() {
    if (initiatedBy == null) {
      return null;
    }
    return initiatedBy.value();
  }

  public void setInitiatedBy(InitiatedByEnum initiatedBy) {
    this.initiatedBy = initiatedBy;
  }

  public Topic initiatedBy(InitiatedByEnum initiatedBy) {
    this.initiatedBy = initiatedBy;
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
    Topic topic = (Topic) o;
    return Objects.equals(this.name, topic.name) &&
        Objects.equals(this.description, topic.description) &&
        Objects.equals(this.topicId, topic.topicId) &&
        Objects.equals(this.status, topic.status) &&
        Objects.equals(this.initiatedBy, topic.initiatedBy);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, description, topicId, status, initiatedBy);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Topic {\n");
    
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    description: ").append(toIndentedString(description)).append("\n");
    sb.append("    topicId: ").append(toIndentedString(topicId)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    initiatedBy: ").append(toIndentedString(initiatedBy)).append("\n");
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

