package org.wso2.dpdp.accelerator.event.notifications.endpoint.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.CompletionStatus;

import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class CompletionRequest  {
  
  @ApiModelProperty(required = true, value = "")

  private CompletionStatus completionStatus;

 /**
  * Absolute HTTPS URL without credentials or a fragment.
  */
  @ApiModelProperty(required = true, value = "Absolute HTTPS URL without credentials or a fragment.")

  private String completionEvidence;

  @ApiModelProperty(value = "")

  private Long completedAt;
 /**
   * Get completionStatus
   * @return completionStatus
  **/
  @JsonProperty("completionStatus")
  public CompletionStatus getCompletionStatus() {
    return completionStatus;
  }

  public void setCompletionStatus(CompletionStatus completionStatus) {
    this.completionStatus = completionStatus;
  }

  public CompletionRequest completionStatus(CompletionStatus completionStatus) {
    this.completionStatus = completionStatus;
    return this;
  }

 /**
   * Absolute HTTPS URL without credentials or a fragment.
   * @return completionEvidence
  **/
  @JsonProperty("completionEvidence")
  public String getCompletionEvidence() {
    return completionEvidence;
  }

  public void setCompletionEvidence(String completionEvidence) {
    this.completionEvidence = completionEvidence;
  }

  public CompletionRequest completionEvidence(String completionEvidence) {
    this.completionEvidence = completionEvidence;
    return this;
  }

 /**
   * Get completedAt
   * minimum: 0
   * @return completedAt
  **/
  @JsonProperty("completedAt")
  public Long getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Long completedAt) {
    this.completedAt = completedAt;
  }

  public CompletionRequest completedAt(Long completedAt) {
    this.completedAt = completedAt;
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
    CompletionRequest completionRequest = (CompletionRequest) o;
    return Objects.equals(this.completionStatus, completionRequest.completionStatus) &&
        Objects.equals(this.completionEvidence, completionRequest.completionEvidence) &&
        Objects.equals(this.completedAt, completionRequest.completedAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(completionStatus, completionEvidence, completedAt);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class CompletionRequest {\n");
    
    sb.append("    completionStatus: ").append(toIndentedString(completionStatus)).append("\n");
    sb.append("    completionEvidence: ").append(toIndentedString(completionEvidence)).append("\n");
    sb.append("    completedAt: ").append(toIndentedString(completedAt)).append("\n");
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

