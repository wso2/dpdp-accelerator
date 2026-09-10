package org.wso2.dpdp.accelerator.event.notifications.endpoint.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.PollSetError;

import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class PollRequest  {
  
 /**
  * Optional compatibility field; when present it must equal the URL tenant.
  */
  @ApiModelProperty(value = "Optional compatibility field; when present it must equal the URL tenant.")

  private String orgId;

  @ApiModelProperty(value = "")

  private Set<String> ack;

  @ApiModelProperty(value = "")

  private Map<String, PollSetError> setErrs;

 /**
  * Uses the configured default when omitted; the configured maximum defaults to 100.
  */
  @ApiModelProperty(value = "Uses the configured default when omitted; the configured maximum defaults to 100.")

  private Integer maxEvents;

 /**
  * Uses the configured default when omitted; false is unsupported.
  */
  @ApiModelProperty(value = "Uses the configured default when omitted; false is unsupported.")

  private Boolean returnImmediately;
 /**
   * Optional compatibility field; when present it must equal the URL tenant.
   * @return orgId
  **/
  @JsonProperty("orgId")
  public String getOrgId() {
    return orgId;
  }

  public void setOrgId(String orgId) {
    this.orgId = orgId;
  }

  public PollRequest orgId(String orgId) {
    this.orgId = orgId;
    return this;
  }

 /**
   * Get ack
   * @return ack
  **/
  @JsonProperty("ack")
  public Set<String> getAck() {
    return ack;
  }

  @JsonDeserialize(as = LinkedHashSet.class)
  public void setAck(Set<String> ack) {
    this.ack = ack;
  }

  public PollRequest ack(Set<String> ack) {
    this.ack = ack;
    return this;
  }

  public PollRequest addAckItem(String ackItem) {
    this.ack.add(ackItem);
    return this;
  }

 /**
   * Get setErrs
   * @return setErrs
  **/
  @JsonProperty("setErrs")
  public Map<String, PollSetError> getSetErrs() {
    return setErrs;
  }

  public void setSetErrs(Map<String, PollSetError> setErrs) {
    this.setErrs = setErrs;
  }

  public PollRequest setErrs(Map<String, PollSetError> setErrs) {
    this.setErrs = setErrs;
    return this;
  }

  public PollRequest putSetErrsItem(String key, PollSetError setErrsItem) {
    this.setErrs.put(key, setErrsItem);
    return this;
  }

 /**
   * Uses the configured default when omitted; the configured maximum defaults to 100.
   * minimum: 0
   * @return maxEvents
  **/
  @JsonProperty("maxEvents")
  public Integer getMaxEvents() {
    return maxEvents;
  }

  public void setMaxEvents(Integer maxEvents) {
    this.maxEvents = maxEvents;
  }

  public PollRequest maxEvents(Integer maxEvents) {
    this.maxEvents = maxEvents;
    return this;
  }

 /**
   * Uses the configured default when omitted; false is unsupported.
   * @return returnImmediately
  **/
  @JsonProperty("returnImmediately")
  public Boolean getReturnImmediately() {
    return returnImmediately;
  }

  public void setReturnImmediately(Boolean returnImmediately) {
    this.returnImmediately = returnImmediately;
  }

  public PollRequest returnImmediately(Boolean returnImmediately) {
    this.returnImmediately = returnImmediately;
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
    PollRequest pollRequest = (PollRequest) o;
    return Objects.equals(this.orgId, pollRequest.orgId) &&
        Objects.equals(this.ack, pollRequest.ack) &&
        Objects.equals(this.setErrs, pollRequest.setErrs) &&
        Objects.equals(this.maxEvents, pollRequest.maxEvents) &&
        Objects.equals(this.returnImmediately, pollRequest.returnImmediately);
  }

  @Override
  public int hashCode() {
    return Objects.hash(orgId, ack, setErrs, maxEvents, returnImmediately);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class PollRequest {\n");
    
    sb.append("    orgId: ").append(toIndentedString(orgId)).append("\n");
    sb.append("    ack: ").append(toIndentedString(ack)).append("\n");
    sb.append("    setErrs: ").append(toIndentedString(setErrs)).append("\n");
    sb.append("    maxEvents: ").append(toIndentedString(maxEvents)).append("\n");
    sb.append("    returnImmediately: ").append(toIndentedString(returnImmediately)).append("\n");
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

