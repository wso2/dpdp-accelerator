package org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto.DeliveryConfigRequest;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto.Filter;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Supply the unique topic names to subscribe to in the topics array.
 */
@ApiModel(description="Supply the unique topic names to subscribe to in the topics array.")

public class SubscriptionCreateRequest  {
  
 /**
  * Unique display name for the subscription within the organization.
  */
  @ApiModelProperty(required = true, value = "Unique display name for the subscription within the organization.")

  private String name;

 /**
  * Unique topic names; duplicates are rejected after normalization.
  */
  @ApiModelProperty(required = true, value = "Unique topic names; duplicates are rejected after normalization.")

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
        Objects.equals(this.topics, subscriptionCreateRequest.topics) &&
        Objects.equals(this.filter, subscriptionCreateRequest.filter) &&
        Objects.equals(this.delivery, subscriptionCreateRequest.delivery);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, topics, filter, delivery);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class SubscriptionCreateRequest {\n");
    
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
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

