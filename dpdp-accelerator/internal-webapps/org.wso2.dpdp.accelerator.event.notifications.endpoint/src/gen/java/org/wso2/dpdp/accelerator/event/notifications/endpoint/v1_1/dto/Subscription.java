package org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto.DeliveryConfig;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto.Filter;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto.SubscriptionStatus;

import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class Subscription  {
  
  @ApiModelProperty(required = true, value = "")

  private String subscriptionId;

 /**
  * Unique display name for the subscription within the organization.
  */
  @ApiModelProperty(required = true, value = "Unique display name for the subscription within the organization.")

  private String name;

 /**
  * Unique topic names in canonical stored form.
  */
  @ApiModelProperty(required = true, value = "Unique topic names in canonical stored form.")

  private List<String> topics;

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

  public Subscription name(String name) {
    this.name = name;
    return this;
  }

 /**
   * Unique topic names in canonical stored form.
   * @return topics
  **/
  @JsonProperty("topics")
  public List<String> getTopics() {
    return topics;
  }

  public void setTopics(List<String> topics) {
    this.topics = topics;
  }

  public Subscription topics(List<String> topics) {
    this.topics = topics;
    return this;
  }

  public Subscription addTopicsItem(String topicsItem) {
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
        Objects.equals(this.name, subscription.name) &&
        Objects.equals(this.topics, subscription.topics) &&
        Objects.equals(this.filter, subscription.filter) &&
        Objects.equals(this.delivery, subscription.delivery) &&
        Objects.equals(this.status, subscription.status) &&
        Objects.equals(this.createdAt, subscription.createdAt) &&
        Objects.equals(this.updatedAt, subscription.updatedAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(subscriptionId, name, topics, filter, delivery, status, createdAt, updatedAt);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Subscription {\n");
    
    sb.append("    subscriptionId: ").append(toIndentedString(subscriptionId)).append("\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    topics: ").append(toIndentedString(topics)).append("\n");
    sb.append("    filter: ").append(toIndentedString(filter)).append("\n");
    sb.append("    delivery: ").append(toIndentedString(delivery)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    createdAt: ").append(toIndentedString(createdAt)).append("\n");
    sb.append("    updatedAt: ").append(toIndentedString(updatedAt)).append("\n");
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

