package org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Acknowledgment that a manual retry has been accepted and queued
 */
@ApiModel(description="Acknowledgment that a manual retry has been accepted and queued")

public class DeliveryRetryResponse  {
  
  @ApiModelProperty(required = true, value = "")

  private String deliveryId;

  @ApiModelProperty(required = true, value = "")

  private String eventId;

  @ApiModelProperty(example = "pending", required = true, value = "")

  private String currentStatus;

  @ApiModelProperty(example = "false", required = true, value = "")

  private Boolean manualRetryAvailable;

  @ApiModelProperty(example = "1787652975000", value = "")

  private Long nextRetryAt;
 /**
   * Get deliveryId
   * @return deliveryId
  **/
  @JsonProperty("deliveryId")
  public String getDeliveryId() {
    return deliveryId;
  }

  public void setDeliveryId(String deliveryId) {
    this.deliveryId = deliveryId;
  }

  public DeliveryRetryResponse deliveryId(String deliveryId) {
    this.deliveryId = deliveryId;
    return this;
  }

 /**
   * Get eventId
   * @return eventId
  **/
  @JsonProperty("eventId")
  public String getEventId() {
    return eventId;
  }

  public void setEventId(String eventId) {
    this.eventId = eventId;
  }

  public DeliveryRetryResponse eventId(String eventId) {
    this.eventId = eventId;
    return this;
  }

 /**
   * Get currentStatus
   * @return currentStatus
  **/
  @JsonProperty("currentStatus")
  public String getCurrentStatus() {
    return currentStatus;
  }

  public void setCurrentStatus(String currentStatus) {
    this.currentStatus = currentStatus;
  }

  public DeliveryRetryResponse currentStatus(String currentStatus) {
    this.currentStatus = currentStatus;
    return this;
  }

 /**
   * Get manualRetryAvailable
   * @return manualRetryAvailable
  **/
  @JsonProperty("manualRetryAvailable")
  public Boolean getManualRetryAvailable() {
    return manualRetryAvailable;
  }

  public void setManualRetryAvailable(Boolean manualRetryAvailable) {
    this.manualRetryAvailable = manualRetryAvailable;
  }

  public DeliveryRetryResponse manualRetryAvailable(Boolean manualRetryAvailable) {
    this.manualRetryAvailable = manualRetryAvailable;
    return this;
  }

 /**
   * Get nextRetryAt
   * @return nextRetryAt
  **/
  @JsonProperty("nextRetryAt")
  public Long getNextRetryAt() {
    return nextRetryAt;
  }

  public void setNextRetryAt(Long nextRetryAt) {
    this.nextRetryAt = nextRetryAt;
  }

  public DeliveryRetryResponse nextRetryAt(Long nextRetryAt) {
    this.nextRetryAt = nextRetryAt;
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
    DeliveryRetryResponse deliveryRetryResponse = (DeliveryRetryResponse) o;
    return Objects.equals(this.deliveryId, deliveryRetryResponse.deliveryId) &&
        Objects.equals(this.eventId, deliveryRetryResponse.eventId) &&
        Objects.equals(this.currentStatus, deliveryRetryResponse.currentStatus) &&
        Objects.equals(this.manualRetryAvailable, deliveryRetryResponse.manualRetryAvailable) &&
        Objects.equals(this.nextRetryAt, deliveryRetryResponse.nextRetryAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(deliveryId, eventId, currentStatus, manualRetryAvailable, nextRetryAt);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class DeliveryRetryResponse {\n");
    
    sb.append("    deliveryId: ").append(toIndentedString(deliveryId)).append("\n");
    sb.append("    eventId: ").append(toIndentedString(eventId)).append("\n");
    sb.append("    currentStatus: ").append(toIndentedString(currentStatus)).append("\n");
    sb.append("    manualRetryAvailable: ").append(toIndentedString(manualRetryAvailable)).append("\n");
    sb.append("    nextRetryAt: ").append(toIndentedString(nextRetryAt)).append("\n");
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

