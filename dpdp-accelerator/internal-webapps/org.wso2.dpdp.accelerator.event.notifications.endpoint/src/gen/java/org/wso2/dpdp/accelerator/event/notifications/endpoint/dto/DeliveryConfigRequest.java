package org.wso2.dpdp.accelerator.event.notifications.endpoint.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.DeliveryMode;

import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class DeliveryConfigRequest  {
  
  @ApiModelProperty(required = true, value = "")

  private DeliveryMode mode;

 /**
  * Required for webhook and omitted for poll.
  */
  @ApiModelProperty(value = "Required for webhook and omitted for poll.")

  private String callbackUrl;

  @ApiModelProperty(required = true, value = "")

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

  public DeliveryConfigRequest mode(DeliveryMode mode) {
    this.mode = mode;
    return this;
  }

 /**
   * Required for webhook and omitted for poll.
   * @return callbackUrl
  **/
  @JsonProperty("callbackUrl")
  public String getCallbackUrl() {
    return callbackUrl;
  }

  public void setCallbackUrl(String callbackUrl) {
    this.callbackUrl = callbackUrl;
  }

  public DeliveryConfigRequest callbackUrl(String callbackUrl) {
    this.callbackUrl = callbackUrl;
    return this;
  }

 /**
   * Get sharedSecret
   * @return sharedSecret
  **/
  @JsonProperty("sharedSecret")
  public String getSharedSecret() {
    return sharedSecret;
  }

  public void setSharedSecret(String sharedSecret) {
    this.sharedSecret = sharedSecret;
  }

  public DeliveryConfigRequest sharedSecret(String sharedSecret) {
    this.sharedSecret = sharedSecret;
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
    DeliveryConfigRequest deliveryConfigRequest = (DeliveryConfigRequest) o;
    return Objects.equals(this.mode, deliveryConfigRequest.mode) &&
        Objects.equals(this.callbackUrl, deliveryConfigRequest.callbackUrl) &&
        Objects.equals(this.sharedSecret, deliveryConfigRequest.sharedSecret);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mode, callbackUrl, sharedSecret);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class DeliveryConfigRequest {\n");
    
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

