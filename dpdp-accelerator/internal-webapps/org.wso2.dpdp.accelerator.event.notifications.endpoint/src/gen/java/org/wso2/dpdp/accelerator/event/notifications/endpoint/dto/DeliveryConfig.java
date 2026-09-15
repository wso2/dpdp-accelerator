package org.wso2.dpdp.accelerator.event.notifications.endpoint.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.DeliveryMode;

import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class DeliveryConfig  {
  
  @ApiModelProperty(required = true, value = "")

  private DeliveryMode mode;

  @ApiModelProperty(value = "")

  private String callbackUrl;

 /**
  * Always null in service responses.
  */
  @ApiModelProperty(value = "Always null in service responses.")

  private String sharedSecret;
 /**
   * Get mode
   * @return mode
  **/
  @JsonProperty("mode")
  public DeliveryMode getMode() {
    return mode;
  }

  public void setMode(DeliveryMode mode) {
    this.mode = mode;
  }

  public DeliveryConfig mode(DeliveryMode mode) {
    this.mode = mode;
    return this;
  }

 /**
   * Get callbackUrl
   * @return callbackUrl
  **/
  @JsonProperty("callbackUrl")
  public String getCallbackUrl() {
    return callbackUrl;
  }

  public void setCallbackUrl(String callbackUrl) {
    this.callbackUrl = callbackUrl;
  }

  public DeliveryConfig callbackUrl(String callbackUrl) {
    this.callbackUrl = callbackUrl;
    return this;
  }

 /**
   * Always null in service responses.
   * @return sharedSecret
  **/
  @JsonProperty("sharedSecret")
  public String getSharedSecret() {
    return sharedSecret;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DeliveryConfig deliveryConfig = (DeliveryConfig) o;
    return Objects.equals(this.mode, deliveryConfig.mode) &&
        Objects.equals(this.callbackUrl, deliveryConfig.callbackUrl) &&
        Objects.equals(this.sharedSecret, deliveryConfig.sharedSecret);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mode, callbackUrl, sharedSecret);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class DeliveryConfig {\n");
    
    sb.append("    mode: ").append(toIndentedString(mode)).append("\n");
    sb.append("    callbackUrl: ").append(toIndentedString(callbackUrl)).append("\n");
    sb.append("    sharedSecret: ").append(toIndentedString(sharedSecret)).append("\n");
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

