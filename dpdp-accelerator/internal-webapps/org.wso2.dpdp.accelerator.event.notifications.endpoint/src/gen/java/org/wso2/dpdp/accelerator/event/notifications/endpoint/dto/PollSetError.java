package org.wso2.dpdp.accelerator.event.notifications.endpoint.dto;


import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class PollSetError  {
  
  @ApiModelProperty(required = true, value = "")

  private String err;

  @ApiModelProperty(required = true, value = "")

  private String description;
 /**
   * Get err
   * @return err
  **/
  @JsonProperty("err")
  public String getErr() {
    return err;
  }

  public void setErr(String err) {
    this.err = err;
  }

  public PollSetError err(String err) {
    this.err = err;
    return this;
  }

 /**
   * Get description
   * @return description
  **/
  @JsonProperty("description")
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public PollSetError description(String description) {
    this.description = description;
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
    PollSetError pollSetError = (PollSetError) o;
    return Objects.equals(this.err, pollSetError.err) &&
        Objects.equals(this.description, pollSetError.description);
  }

  @Override
  public int hashCode() {
    return Objects.hash(err, description);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class PollSetError {\n");
    
    sb.append("    err: ").append(toIndentedString(err)).append("\n");
    sb.append("    description: ").append(toIndentedString(description)).append("\n");
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

