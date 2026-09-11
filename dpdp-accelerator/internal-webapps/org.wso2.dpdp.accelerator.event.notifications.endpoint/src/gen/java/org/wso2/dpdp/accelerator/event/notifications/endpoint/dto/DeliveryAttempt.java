package org.wso2.dpdp.accelerator.event.notifications.endpoint.dto;


import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class DeliveryAttempt  {
  
  @ApiModelProperty(required = true, value = "")

  private Integer attempt;

  @ApiModelProperty(required = true, value = "")

  private String status;

  @ApiModelProperty(required = true, value = "")

  private Long timestamp;

  @ApiModelProperty(value = "")

  private Integer httpStatus;

  @ApiModelProperty(value = "")

  private String error;
 /**
   * Get attempt
   * @return attempt
  **/
  @JsonProperty("attempt")
  public Integer getAttempt() {
    return attempt;
  }

  public void setAttempt(Integer attempt) {
    this.attempt = attempt;
  }

  public DeliveryAttempt attempt(Integer attempt) {
    this.attempt = attempt;
    return this;
  }

 /**
   * Get status
   * @return status
  **/
  @JsonProperty("status")
  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public DeliveryAttempt status(String status) {
    this.status = status;
    return this;
  }

 /**
   * Get timestamp
   * @return timestamp
  **/
  @JsonProperty("timestamp")
  public Long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Long timestamp) {
    this.timestamp = timestamp;
  }

  public DeliveryAttempt timestamp(Long timestamp) {
    this.timestamp = timestamp;
    return this;
  }

 /**
   * Get httpStatus
   * @return httpStatus
  **/
  @JsonProperty("httpStatus")
  public Integer getHttpStatus() {
    return httpStatus;
  }

  public void setHttpStatus(Integer httpStatus) {
    this.httpStatus = httpStatus;
  }

  public DeliveryAttempt httpStatus(Integer httpStatus) {
    this.httpStatus = httpStatus;
    return this;
  }

 /**
   * Get error
   * @return error
  **/
  @JsonProperty("error")
  public String getError() {
    return error;
  }

  public void setError(String error) {
    this.error = error;
  }

  public DeliveryAttempt error(String error) {
    this.error = error;
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
    DeliveryAttempt deliveryAttempt = (DeliveryAttempt) o;
    return Objects.equals(this.attempt, deliveryAttempt.attempt) &&
        Objects.equals(this.status, deliveryAttempt.status) &&
        Objects.equals(this.timestamp, deliveryAttempt.timestamp) &&
        Objects.equals(this.httpStatus, deliveryAttempt.httpStatus) &&
        Objects.equals(this.error, deliveryAttempt.error);
  }

  @Override
  public int hashCode() {
    return Objects.hash(attempt, status, timestamp, httpStatus, error);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class DeliveryAttempt {\n");
    
    sb.append("    attempt: ").append(toIndentedString(attempt)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    timestamp: ").append(toIndentedString(timestamp)).append("\n");
    sb.append("    httpStatus: ").append(toIndentedString(httpStatus)).append("\n");
    sb.append("    error: ").append(toIndentedString(error)).append("\n");
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

