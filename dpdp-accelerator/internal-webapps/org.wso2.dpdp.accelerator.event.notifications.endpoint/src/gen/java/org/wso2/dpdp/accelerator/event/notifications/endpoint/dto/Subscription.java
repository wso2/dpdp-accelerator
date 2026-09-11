package org.wso2.dpdp.accelerator.event.notifications.endpoint.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.DeliveryConfig;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.Filter;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SubscriptionStatus;

import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class Subscription  {
  
  @ApiModelProperty(required = true, value = "")

  private String subscriptionId;

  @ApiModelProperty(required = true, value = "")

  private String orgId;

  @ApiModelProperty(required = true, value = "")

  private String groupId;

  @ApiModelProperty(required = true, value = "")

  private String topic;

  @ApiModelProperty(required = true, value = "")

  private Filter filter;

  @ApiModelProperty(required = true, value = "")

  private DeliveryConfig delivery;

  @ApiModelProperty(required = true, value = "")

  private SubscriptionStatus status;

  @ApiModelProperty(value = "")

  private Long createdAt;

  @ApiModelProperty(value = "")

  private Long updatedAt;

  @ApiModelProperty(value = "")

  private Boolean alreadyExists;

  @ApiModelProperty(value = "")

  private String message;
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

  public Subscription subscriptionId(String subscriptionId) {
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

  public Subscription orgId(String orgId) {
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

  public Subscription groupId(String groupId) {
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

  public Subscription topic(String topic) {
    this.topic = topic;
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

  public Subscription filter(Filter filter) {
    this.filter = filter;
    return this;
  }

 /**
   * Get delivery
   * @return delivery
  **/
  @JsonProperty("delivery")
  public DeliveryConfig getDelivery() {
    return delivery;
  }

  public void setDelivery(DeliveryConfig delivery) {
    this.delivery = delivery;
  }

  public Subscription delivery(DeliveryConfig delivery) {
    this.delivery = delivery;
    return this;
  }

 /**
   * Get status
   * @return status
  **/
  @JsonProperty("status")
  public SubscriptionStatus getStatus() {
    return status;
  }

  public void setStatus(SubscriptionStatus status) {
    this.status = status;
  }

  public Subscription status(SubscriptionStatus status) {
    this.status = status;
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

  public Subscription createdAt(Long createdAt) {
    this.createdAt = createdAt;
    return this;
  }

 /**
   * Get updatedAt
   * @return updatedAt
  **/
  @JsonProperty("updatedAt")
  public Long getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Long updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Subscription updatedAt(Long updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }

 /**
   * Get alreadyExists
   * @return alreadyExists
  **/
  @JsonProperty("alreadyExists")
  public Boolean getAlreadyExists() {
    return alreadyExists;
  }

  public void setAlreadyExists(Boolean alreadyExists) {
    this.alreadyExists = alreadyExists;
  }

  public Subscription alreadyExists(Boolean alreadyExists) {
    this.alreadyExists = alreadyExists;
    return this;
  }

 /**
   * Get message
   * @return message
  **/
  @JsonProperty("message")
  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Subscription message(String message) {
    this.message = message;
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
    Subscription subscription = (Subscription) o;
    return Objects.equals(this.subscriptionId, subscription.subscriptionId) &&
        Objects.equals(this.orgId, subscription.orgId) &&
        Objects.equals(this.groupId, subscription.groupId) &&
        Objects.equals(this.topic, subscription.topic) &&
        Objects.equals(this.filter, subscription.filter) &&
        Objects.equals(this.delivery, subscription.delivery) &&
        Objects.equals(this.status, subscription.status) &&
        Objects.equals(this.createdAt, subscription.createdAt) &&
        Objects.equals(this.updatedAt, subscription.updatedAt) &&
        Objects.equals(this.alreadyExists, subscription.alreadyExists) &&
        Objects.equals(this.message, subscription.message);
  }

  @Override
  public int hashCode() {
    return Objects.hash(subscriptionId, orgId, groupId, topic, filter, delivery, status, createdAt, updatedAt, alreadyExists, message);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Subscription {\n");
    
    sb.append("    subscriptionId: ").append(toIndentedString(subscriptionId)).append("\n");
    sb.append("    orgId: ").append(toIndentedString(orgId)).append("\n");
    sb.append("    groupId: ").append(toIndentedString(groupId)).append("\n");
    sb.append("    topic: ").append(toIndentedString(topic)).append("\n");
    sb.append("    filter: ").append(toIndentedString(filter)).append("\n");
    sb.append("    delivery: ").append(toIndentedString(delivery)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    createdAt: ").append(toIndentedString(createdAt)).append("\n");
    sb.append("    updatedAt: ").append(toIndentedString(updatedAt)).append("\n");
    sb.append("    alreadyExists: ").append(toIndentedString(alreadyExists)).append("\n");
    sb.append("    message: ").append(toIndentedString(message)).append("\n");
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

