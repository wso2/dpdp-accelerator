package org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto.SubscriptionStatus;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of a subscription status change such as verification or deletion.
 */
@ApiModel(description="Result of a subscription status change such as verification or deletion.")

public class SubscriptionStatusUpdate  {
  
  @ApiModelProperty(required = true, value = "")

  private String subscriptionId;

  @ApiModelProperty(required = true, value = "")

  private SubscriptionStatus status;

  @ApiModelProperty(required = true, value = "")

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

  public SubscriptionStatusUpdate subscriptionId(String subscriptionId) {
    this.subscriptionId = subscriptionId;
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

  public SubscriptionStatusUpdate status(SubscriptionStatus status) {
    this.status = status;
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

  public SubscriptionStatusUpdate updatedAt(Long updatedAt) {
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
    SubscriptionStatusUpdate subscriptionStatusUpdate = (SubscriptionStatusUpdate) o;
    return Objects.equals(this.subscriptionId, subscriptionStatusUpdate.subscriptionId) &&
        Objects.equals(this.status, subscriptionStatusUpdate.status) &&
        Objects.equals(this.updatedAt, subscriptionStatusUpdate.updatedAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(subscriptionId, status, updatedAt);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class SubscriptionStatusUpdate {\n");
    
    sb.append("    subscriptionId: ").append(toIndentedString(subscriptionId)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
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

