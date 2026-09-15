package org.wso2.dpdp.accelerator.event.notifications.endpoint.dto;


import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class TopicCreateRequest  {
  
 /**
  * Accepted for compatibility; ignored on creation.
  */
  @ApiModelProperty(value = "Accepted for compatibility; ignored on creation.")

  private String topicId;

 /**
  * Accepted for compatibility; ignored on creation.
  */
  @ApiModelProperty(value = "Accepted for compatibility; ignored on creation.")

  private String status;

 /**
  * Accepted for compatibility; ignored on creation.
  */
  @ApiModelProperty(value = "Accepted for compatibility; ignored on creation.")

  private String initiatedBy;

  @ApiModelProperty(required = true, value = "")

  private String name;

  @ApiModelProperty(value = "")

  private String description;
 /**
   * Accepted for compatibility; ignored on creation.
   * @return topicId
  **/
  @JsonProperty("topicId")
  public String getTopicId() {
    return topicId;
  }

  public void setTopicId(String topicId) {
    this.topicId = topicId;
  }

  public TopicCreateRequest topicId(String topicId) {
    this.topicId = topicId;
    return this;
  }

 /**
   * Accepted for compatibility; ignored on creation.
   * @return status
  **/
  @JsonProperty("status")
  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public TopicCreateRequest status(String status) {
    this.status = status;
    return this;
  }

 /**
   * Accepted for compatibility; ignored on creation.
   * @return initiatedBy
  **/
  @JsonProperty("initiatedBy")
  public String getInitiatedBy() {
    return initiatedBy;
  }

  public void setInitiatedBy(String initiatedBy) {
    this.initiatedBy = initiatedBy;
  }

  public TopicCreateRequest initiatedBy(String initiatedBy) {
    this.initiatedBy = initiatedBy;
    return this;
  }

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

  public TopicCreateRequest name(String name) {
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

  public TopicCreateRequest description(String description) {
    this.description = description;
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
    TopicCreateRequest topicCreateRequest = (TopicCreateRequest) o;
    return Objects.equals(this.topicId, topicCreateRequest.topicId) &&
        Objects.equals(this.status, topicCreateRequest.status) &&
        Objects.equals(this.initiatedBy, topicCreateRequest.initiatedBy) &&
        Objects.equals(this.name, topicCreateRequest.name) &&
        Objects.equals(this.description, topicCreateRequest.description);
  }

  @Override
  public int hashCode() {
    return Objects.hash(topicId, status, initiatedBy, name, description);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class TopicCreateRequest {\n");
    
    sb.append("    topicId: ").append(toIndentedString(topicId)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    initiatedBy: ").append(toIndentedString(initiatedBy)).append("\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    description: ").append(toIndentedString(description)).append("\n");
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

