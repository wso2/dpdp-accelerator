package org.wso2.dpdp.accelerator.event.notifications.endpoint.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.DeliveryConfigRequest;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.Filter;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SubscriptionStatus;

import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class SubscriptionCreateRequest  {
  
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

  @ApiModelProperty(value = "")

  private SubscriptionStatus status;

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

  @ApiModelProperty(required = true, value = "")

  private String topic;

  @ApiModelProperty(value = "")

  private Filter filter;

  @ApiModelProperty(required = true, value = "")

  private DeliveryConfigRequest delivery;
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

  public SubscriptionCreateRequest status(SubscriptionStatus status) {
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

  public SubscriptionCreateRequest topic(String topic) {
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
    return Objects.equals(this.subscriptionId, subscriptionCreateRequest.subscriptionId) &&
        Objects.equals(this.orgId, subscriptionCreateRequest.orgId) &&
        Objects.equals(this.groupId, subscriptionCreateRequest.groupId) &&
        Objects.equals(this.status, subscriptionCreateRequest.status) &&
        Objects.equals(this.createdAt, subscriptionCreateRequest.createdAt) &&
        Objects.equals(this.updatedAt, subscriptionCreateRequest.updatedAt) &&
        Objects.equals(this.alreadyExists, subscriptionCreateRequest.alreadyExists) &&
        Objects.equals(this.message, subscriptionCreateRequest.message) &&
        Objects.equals(this.topic, subscriptionCreateRequest.topic) &&
        Objects.equals(this.filter, subscriptionCreateRequest.filter) &&
        Objects.equals(this.delivery, subscriptionCreateRequest.delivery);
  }

  @Override
  public int hashCode() {
    return Objects.hash(subscriptionId, orgId, groupId, status, createdAt, updatedAt, alreadyExists, message, topic, filter, delivery);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class SubscriptionCreateRequest {\n");
    
    sb.append("    subscriptionId: ").append(toIndentedString(subscriptionId)).append("\n");
    sb.append("    orgId: ").append(toIndentedString(orgId)).append("\n");
    sb.append("    groupId: ").append(toIndentedString(groupId)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    createdAt: ").append(toIndentedString(createdAt)).append("\n");
    sb.append("    updatedAt: ").append(toIndentedString(updatedAt)).append("\n");
    sb.append("    alreadyExists: ").append(toIndentedString(alreadyExists)).append("\n");
    sb.append("    message: ").append(toIndentedString(message)).append("\n");
    sb.append("    topic: ").append(toIndentedString(topic)).append("\n");
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

