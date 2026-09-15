package org.wso2.dpdp.accelerator.event.notifications.endpoint.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.PurposeFilterMode;

import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class Filter  {
  
  @ApiModelProperty(required = true, value = "")

  private PurposeFilterMode type;

  @ApiModelProperty(value = "")

  private List<String> purposes;
 /**
   * Get type
   * @return type
  **/
  @JsonProperty("type")
  public PurposeFilterMode getType() {
    return type;
  }

  public void setType(PurposeFilterMode type) {
    this.type = type;
  }

  public Filter type(PurposeFilterMode type) {
    this.type = type;
    return this;
  }

 /**
   * Get purposes
   * @return purposes
  **/
  @JsonProperty("purposes")
  public List<String> getPurposes() {
    return purposes;
  }

  public void setPurposes(List<String> purposes) {
    this.purposes = purposes;
  }

  public Filter purposes(List<String> purposes) {
    this.purposes = purposes;
    return this;
  }

  public Filter addPurposesItem(String purposesItem) {
    this.purposes.add(purposesItem);
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
    Filter filter = (Filter) o;
    return Objects.equals(this.type, filter.type) &&
        Objects.equals(this.purposes, filter.purposes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, purposes);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Filter {\n");
    
    sb.append("    type: ").append(toIndentedString(type)).append("\n");
    sb.append("    purposes: ").append(toIndentedString(purposes)).append("\n");
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

