package org.wso2.dpdp.accelerator.event.notifications.endpoint.dto;

import java.util.HashMap;
import java.util.Map;

import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class PollResponse  {
  
  @ApiModelProperty(required = true, value = "")

  private Boolean moreAvailable;

 /**
  * Map of deliveryId to compact RS256 JWS.
  */
  @ApiModelProperty(required = true, value = "Map of deliveryId to compact RS256 JWS.")

  private Map<String, String> sets;
 /**
   * Get moreAvailable
   * @return moreAvailable
  **/
  @JsonProperty("moreAvailable")
  public Boolean getMoreAvailable() {
    return moreAvailable;
  }

  public void setMoreAvailable(Boolean moreAvailable) {
    this.moreAvailable = moreAvailable;
  }

  public PollResponse moreAvailable(Boolean moreAvailable) {
    this.moreAvailable = moreAvailable;
    return this;
  }

 /**
   * Map of deliveryId to compact RS256 JWS.
   * @return sets
  **/
  @JsonProperty("sets")
  public Map<String, String> getSets() {
    return sets;
  }

  public void setSets(Map<String, String> sets) {
    this.sets = sets;
  }

  public PollResponse sets(Map<String, String> sets) {
    this.sets = sets;
    return this;
  }

  public PollResponse putSetsItem(String key, String setsItem) {
    this.sets.put(key, setsItem);
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
    PollResponse pollResponse = (PollResponse) o;
    return Objects.equals(this.moreAvailable, pollResponse.moreAvailable) &&
        Objects.equals(this.sets, pollResponse.sets);
  }

  @Override
  public int hashCode() {
    return Objects.hash(moreAvailable, sets);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class PollResponse {\n");
    
    sb.append("    moreAvailable: ").append(toIndentedString(moreAvailable)).append("\n");
    sb.append("    sets: ").append(toIndentedString(sets)).append("\n");
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

