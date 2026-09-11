package org.wso2.dpdp.accelerator.event.notifications.endpoint.dto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.Event;

import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;


public class EventPage  {
  
  @ApiModelProperty(required = true, value = "")

  private List<Event> items;

  @ApiModelProperty(required = true, value = "")

  private Integer total;
 /**
   * Get items
   * @return items
  **/
  @JsonProperty("items")
  public List<Event> getItems() {
    return items;
  }

  public void setItems(List<Event> items) {
    this.items = items;
  }

  public EventPage items(List<Event> items) {
    this.items = items;
    return this;
  }

  public EventPage addItemsItem(Event itemsItem) {
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

  public EventPage total(Integer total) {
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
    EventPage eventPage = (EventPage) o;
    return Objects.equals(this.items, eventPage.items) &&
        Objects.equals(this.total, eventPage.total);
  }

  @Override
  public int hashCode() {
    return Objects.hash(items, total);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class EventPage {\n");
    
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

