package org.wso2.dpdp.accelerator.event.notifications.endpoint.dto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.Delivery;

import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class DeliveryPage  {
  
  @ApiModelProperty(required = true, value = "")

  private List<Delivery> items;

  @ApiModelProperty(required = true, value = "")

  private Integer total;
 /**
   * Get items
   * @return items
  **/
  @JsonProperty("items")
  public List<Delivery> getItems() {
    return items;
  }

  public void setItems(List<Delivery> items) {
    this.items = items;
  }

  public DeliveryPage items(List<Delivery> items) {
    this.items = items;
    return this;
  }

  public DeliveryPage addItemsItem(Delivery itemsItem) {
    this.items.add(itemsItem);
    return this;
  }

 /**
   * Get total
   * @return total
  **/
  @JsonProperty("total")
  public Integer getTotal() {
    return total;
  }

  public void setTotal(Integer total) {
    this.total = total;
  }

  public DeliveryPage total(Integer total) {
    this.total = total;
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
    DeliveryPage deliveryPage = (DeliveryPage) o;
    return Objects.equals(this.items, deliveryPage.items) &&
        Objects.equals(this.total, deliveryPage.total);
  }

  @Override
  public int hashCode() {
    return Objects.hash(items, total);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class DeliveryPage {\n");
    
    sb.append("    items: ").append(toIndentedString(items)).append("\n");
    sb.append("    total: ").append(toIndentedString(total)).append("\n");
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

