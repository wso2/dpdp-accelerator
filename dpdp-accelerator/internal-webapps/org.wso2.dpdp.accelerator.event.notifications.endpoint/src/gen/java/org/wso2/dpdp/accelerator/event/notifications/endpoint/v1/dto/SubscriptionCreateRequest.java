package org.wso2.dpdp.accelerator.event.notifications.endpoint.v1.dto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1.dto.DeliveryConfigRequest;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1.dto.Filter;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Supply topics (array) or the legacy singleton topic field, but not both.
 */
@ApiModel(description="Supply topics (array) or the legacy singleton topic field, but not both.")

public class SubscriptionCreateRequest  {
  
 /**
  * Unique display name for the subscription within the organization.
  */
  @ApiModelProperty(required = true, value = "Unique display name for the subscription within the organization.")

  private String name;

 /**
  * Accepted for compatibility; ignored on creation.
  */
  @ApiModelProperty(value = "Accepted for compatibility; ignored on creation.")

  private String subscriptionId;

 /**
  * Accepted for compatibility; tenant context determines the organization.
  */
  @ApiModelProperty(value = "Accepted for compatibility; tenant context determines the organization.")

  private String orgId;

 /**
  * Accepted for compatibility; the handler derives the group from tenant context.
  */
  @ApiModelProperty(value = "Accepted for compatibility; the handler derives the group from tenant context.")

  private String groupId;

 /**
  * Accepted for compatibility; ignored on creation.
  */
  @ApiModelProperty(value = "Accepted for compatibility; ignored on creation.")

  private String status;

 /**
  * Accepted for compatibility; ignored on creation.
  */
  @ApiModelProperty(value = "Accepted for compatibility; ignored on creation.")

  private Long createdAt;

 /**
  * Accepted for compatibility; ignored on creation.
  */
  @ApiModelProperty(value = "Accepted for compatibility; ignored on creation.")

  private Long updatedAt;

 /**
  * Accepted for compatibility; ignored on creation.
  */
  @ApiModelProperty(value = "Accepted for compatibility; ignored on creation.")

  private Boolean alreadyExists;

 /**
  * Accepted for compatibility; ignored on creation.
  */
  @ApiModelProperty(value = "Accepted for compatibility; ignored on creation.")

  private String message;

 /**
  * Singleton compatibility field; use topics for new integrations.
  */
  @ApiModelProperty(value = "Singleton compatibility field; use topics for new integrations.")

  private String topic;

 /**
  * Unique topic names; duplicates are rejected after normalization.
  */
  @ApiModelProperty(value = "Unique topic names; duplicates are rejected after normalization.")

  private List<String> topics;

  @ApiModelProperty(value = "")

  private Filter filter;

  @ApiModelProperty(required = true, value = "")

  private DeliveryConfigRequest delivery;
 /**
   * Unique display name for the subscription within the organization.
   * @return name
  **/
  @JsonProperty("name")
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public SubscriptionCreateRequest name(String name) {
    this.name = name;
    return this;
  }

 /**
   * Accepted for compatibility; ignored on creation.
   * @return subscriptionId
  **/
  @JsonProperty("subscriptionId")
  public String getSubscriptionId() {
    return subscriptionId;
  }

  public void setSubscriptionId(String subscriptionId) {
    this.subscriptionId = subscriptionId;
  }

  public SubscriptionCreateRequest subscriptionId(String subscriptionId) {
    this.subscriptionId = subscriptionId;
    return this;
  }

 /**
   * Accepted for compatibility; tenant context determines the organization.
   * @return orgId
  **/
  @JsonProperty("orgId")
  public String getOrgId() {
    return orgId;
  }

  public void setOrgId(String orgId) {
    this.orgId = orgId;
  }

  public SubscriptionCreateRequest orgId(String orgId) {
    this.orgId = orgId;
    return this;
  }

 /**
   * Accepted for compatibility; the handler derives the group from tenant context.
   * @return groupId
  **/
  @JsonProperty("groupId")
  public String getGroupId() {
    return groupId;
  }

  public void setGroupId(String groupId) {
    this.groupId = groupId;
  }

  public SubscriptionCreateRequest groupId(String groupId) {
    this.groupId = groupId;
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

  public SubscriptionCreateRequest status(String status) {
    this.status = status;
    return this;
  }

 /**
   * Accepted for compatibility; ignored on creation.
   * @return createdAt
  **/
  @JsonProperty("createdAt")
  public Long getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Long createdAt) {
    this.createdAt = createdAt;
  }

  public SubscriptionCreateRequest createdAt(Long createdAt) {
    this.createdAt = createdAt;
    return this;
  }

 /**
   * Accepted for compatibility; ignored on creation.
   * @return updatedAt
  **/
  @JsonProperty("updatedAt")
  public Long getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Long updatedAt) {
    this.updatedAt = updatedAt;
  }

  public SubscriptionCreateRequest updatedAt(Long updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }

 /**
   * Accepted for compatibility; ignored on creation.
   * @return alreadyExists
  **/
  @JsonProperty("alreadyExists")
  public Boolean getAlreadyExists() {
    return alreadyExists;
  }

  public void setAlreadyExists(Boolean alreadyExists) {
    this.alreadyExists = alreadyExists;
  }

  public SubscriptionCreateRequest alreadyExists(Boolean alreadyExists) {
    this.alreadyExists = alreadyExists;
    return this;
  }

 /**
   * Accepted for compatibility; ignored on creation.
   * @return message
  **/
  @JsonProperty("message")
  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public SubscriptionCreateRequest message(String message) {
    this.message = message;
    return this;
  }

 /**
   * Singleton compatibility field; use topics for new integrations.
   * @return topic
  **/
  @JsonProperty("topic")
  public String getTopic() {
    return topic;
  }

  public void setTopic(String topic) {
    this.topic = topic;
  }

  public SubscriptionCreateRequest topic(String topic) {
    this.topic = topic;
    return this;
  }

 /**
   * Unique topic names; duplicates are rejected after normalization.
   * @return topics
  **/
  @JsonProperty("topics")
  public List<String> getTopics() {
    return topics;
  }

  public void setTopics(List<String> topics) {
    this.topics = topics;
  }

  public SubscriptionCreateRequest topics(List<String> topics) {
    this.topics = topics;
    return this;
  }

  public SubscriptionCreateRequest addTopicsItem(String topicsItem) {
    this.topics.add(topicsItem);
    return this;
  }

 /**
   * Get filter
   * @return filter
  **/
  @JsonProperty("filter")
  public Filter getFilter() {
    return filter;
  }

  public void setFilter(Filter filter) {
    this.filter = filter;
  }

  public SubscriptionCreateRequest filter(Filter filter) {
    this.filter = filter;
    return this;
  }

 /**
   * Get delivery
   * @return delivery
  **/
  @JsonProperty("delivery")
  public DeliveryConfigRequest getDelivery() {
    return delivery;
  }

  public void setDelivery(DeliveryConfigRequest delivery) {
    this.delivery = delivery;
  }

  public SubscriptionCreateRequest delivery(DeliveryConfigRequest delivery) {
    this.delivery = delivery;
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
    SubscriptionCreateRequest subscriptionCreateRequest = (SubscriptionCreateRequest) o;
    return Objects.equals(this.name, subscriptionCreateRequest.name) &&
        Objects.equals(this.subscriptionId, subscriptionCreateRequest.subscriptionId) &&
        Objects.equals(this.orgId, subscriptionCreateRequest.orgId) &&
        Objects.equals(this.groupId, subscriptionCreateRequest.groupId) &&
        Objects.equals(this.status, subscriptionCreateRequest.status) &&
        Objects.equals(this.createdAt, subscriptionCreateRequest.createdAt) &&
        Objects.equals(this.updatedAt, subscriptionCreateRequest.updatedAt) &&
        Objects.equals(this.alreadyExists, subscriptionCreateRequest.alreadyExists) &&
        Objects.equals(this.message, subscriptionCreateRequest.message) &&
        Objects.equals(this.topic, subscriptionCreateRequest.topic) &&
        Objects.equals(this.topics, subscriptionCreateRequest.topics) &&
        Objects.equals(this.filter, subscriptionCreateRequest.filter) &&
        Objects.equals(this.delivery, subscriptionCreateRequest.delivery);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, subscriptionId, orgId, groupId, status, createdAt, updatedAt, alreadyExists, message, topic, topics, filter, delivery);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class SubscriptionCreateRequest {\n");
    
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    subscriptionId: ").append(toIndentedString(subscriptionId)).append("\n");
    sb.append("    orgId: ").append(toIndentedString(orgId)).append("\n");
    sb.append("    groupId: ").append(toIndentedString(groupId)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    createdAt: ").append(toIndentedString(createdAt)).append("\n");
    sb.append("    updatedAt: ").append(toIndentedString(updatedAt)).append("\n");
    sb.append("    alreadyExists: ").append(toIndentedString(alreadyExists)).append("\n");
    sb.append("    message: ").append(toIndentedString(message)).append("\n");
    sb.append("    topic: ").append(toIndentedString(topic)).append("\n");
    sb.append("    topics: ").append(toIndentedString(topics)).append("\n");
    sb.append("    filter: ").append(toIndentedString(filter)).append("\n");
    sb.append("    delivery: ").append(toIndentedString(delivery)).append("\n");
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

