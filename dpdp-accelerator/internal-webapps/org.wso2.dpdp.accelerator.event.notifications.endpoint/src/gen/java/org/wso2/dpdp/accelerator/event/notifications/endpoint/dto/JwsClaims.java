package org.wso2.dpdp.accelerator.event.notifications.endpoint.dto;

import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SignedEventEnvelope;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Decoded JWS claims. The iss value is the issuer configured by Identity Server for the tenant. The protected header also contains alg=RS256, typ=JWT, kid, and x5t#S256. The kid is resolved through the configured Identity Server KeyIDProvider and must match an entry from the trusted tenant JWKS endpoint.
 */
@ApiModel(description="Decoded JWS claims. The iss value is the issuer configured by Identity Server for the tenant. The protected header also contains alg=RS256, typ=JWT, kid, and x5t#S256. The kid is resolved through the configured Identity Server KeyIDProvider and must match an entry from the trusted tenant JWKS endpoint.")

public class JwsClaims  {
  
  @ApiModelProperty(example = "https://is.example.com:9443/t/example.com/oauth2/token", required = true, value = "")

  private String iss;

  @ApiModelProperty(example = "processor-1", required = true, value = "")

  private String sub;

  @ApiModelProperty(example = "dpdp-event-notifications", required = true, value = "")

  private String aud;

  @ApiModelProperty(required = true, value = "")

  private Long iat;

  @ApiModelProperty(required = true, value = "")

  private String jti;

  @ApiModelProperty(required = true, value = "")

  private String txn;

  @ApiModelProperty(required = true, value = "")

  private String payloadHash;

  @ApiModelProperty(required = true, value = "")

  private SignedEventEnvelope payload;
 /**
   * Get iss
   * @return iss
  **/
  @JsonProperty("iss")
  public String getIss() {
    return iss;
  }

  public void setIss(String iss) {
    this.iss = iss;
  }

  public JwsClaims iss(String iss) {
    this.iss = iss;
    return this;
  }

 /**
   * Get sub
   * @return sub
  **/
  @JsonProperty("sub")
  public String getSub() {
    return sub;
  }

  public void setSub(String sub) {
    this.sub = sub;
  }

  public JwsClaims sub(String sub) {
    this.sub = sub;
    return this;
  }

 /**
   * Get aud
   * @return aud
  **/
  @JsonProperty("aud")
  public String getAud() {
    return aud;
  }

  public void setAud(String aud) {
    this.aud = aud;
  }

  public JwsClaims aud(String aud) {
    this.aud = aud;
    return this;
  }

 /**
   * Get iat
   * @return iat
  **/
  @JsonProperty("iat")
  public Long getIat() {
    return iat;
  }

  public void setIat(Long iat) {
    this.iat = iat;
  }

  public JwsClaims iat(Long iat) {
    this.iat = iat;
    return this;
  }

 /**
   * Get jti
   * @return jti
  **/
  @JsonProperty("jti")
  public String getJti() {
    return jti;
  }

  public void setJti(String jti) {
    this.jti = jti;
  }

  public JwsClaims jti(String jti) {
    this.jti = jti;
    return this;
  }

 /**
   * Get txn
   * @return txn
  **/
  @JsonProperty("txn")
  public String getTxn() {
    return txn;
  }

  public void setTxn(String txn) {
    this.txn = txn;
  }

  public JwsClaims txn(String txn) {
    this.txn = txn;
    return this;
  }

 /**
   * Get payloadHash
   * @return payloadHash
  **/
  @JsonProperty("payloadHash")
  public String getPayloadHash() {
    return payloadHash;
  }

  public void setPayloadHash(String payloadHash) {
    this.payloadHash = payloadHash;
  }

  public JwsClaims payloadHash(String payloadHash) {
    this.payloadHash = payloadHash;
    return this;
  }

 /**
   * Get payload
   * @return payload
  **/
  @JsonProperty("payload")
  public SignedEventEnvelope getPayload() {
    return payload;
  }

  public void setPayload(SignedEventEnvelope payload) {
    this.payload = payload;
  }

  public JwsClaims payload(SignedEventEnvelope payload) {
    this.payload = payload;
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
    JwsClaims jwsClaims = (JwsClaims) o;
    return Objects.equals(this.iss, jwsClaims.iss) &&
        Objects.equals(this.sub, jwsClaims.sub) &&
        Objects.equals(this.aud, jwsClaims.aud) &&
        Objects.equals(this.iat, jwsClaims.iat) &&
        Objects.equals(this.jti, jwsClaims.jti) &&
        Objects.equals(this.txn, jwsClaims.txn) &&
        Objects.equals(this.payloadHash, jwsClaims.payloadHash) &&
        Objects.equals(this.payload, jwsClaims.payload);
  }

  @Override
  public int hashCode() {
    return Objects.hash(iss, sub, aud, iat, jti, txn, payloadHash, payload);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class JwsClaims {\n");
    
    sb.append("    iss: ").append(toIndentedString(iss)).append("\n");
    sb.append("    sub: ").append(toIndentedString(sub)).append("\n");
    sb.append("    aud: ").append(toIndentedString(aud)).append("\n");
    sb.append("    iat: ").append(toIndentedString(iat)).append("\n");
    sb.append("    jti: ").append(toIndentedString(jti)).append("\n");
    sb.append("    txn: ").append(toIndentedString(txn)).append("\n");
    sb.append("    payloadHash: ").append(toIndentedString(payloadHash)).append("\n");
    sb.append("    payload: ").append(toIndentedString(payload)).append("\n");
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

