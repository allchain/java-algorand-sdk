/*
 * Algod REST API.
 * API Endpoint for AlgoD Operations.
 *
 * OpenAPI spec version: 0.0.1
 * Contact: contact@algorand.com
 *
 * NOTE: This class is auto generated by the swagger code generator program.
 * https://github.com/swagger-api/swagger-codegen.git
 * Do not edit the class manually.
 */


package com.algorand.algosdk.algod.client.model;

import com.google.gson.annotations.SerializedName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.ObjectUtils;

/**
 * PaymentTransactionType contains the additional fields for a payment Transaction
 */
@ApiModel(description = "PaymentTransactionType contains the additional fields for a payment Transaction")

public class PaymentTransactionType {
  @SerializedName("amount")
  private java.math.BigInteger amount = null;

  @SerializedName("close")
  private String close = null;

  @SerializedName("to")
  private String to = null;

  public PaymentTransactionType amount(java.math.BigInteger amount) {
    this.amount = amount;
    return this;
  }

   /**
   * Amount is the amount of Algos intended to be transferred
   * @return amount
  **/
  @ApiModelProperty(required = true, value = "Amount is the amount of Algos intended to be transferred")
  public java.math.BigInteger getAmount() {
    return amount;
  }

  public void setAmount(java.math.BigInteger amount) {
    this.amount = amount;
  }

  public PaymentTransactionType close(String close) {
    this.close = close;
    return this;
  }

   /**
   * CloseRemainderTo is the address the sender closed to
   * @return close
  **/
  @ApiModelProperty(value = "CloseRemainderTo is the address the sender closed to")
  public String getClose() {
    return close;
  }

  public void setClose(String close) {
    this.close = close;
  }

  public PaymentTransactionType to(String to) {
    this.to = to;
    return this;
  }

   /**
   * To is the receiver&#39;s address
   * @return to
  **/
  @ApiModelProperty(required = true, value = "To is the receiver's address")
  public String getTo() {
    return to;
  }

  public void setTo(String to) {
    this.to = to;
  }


  @Override
  public boolean equals(java.lang.Object o) {
  if (this == o) {
    return true;
  }
  if (o == null || getClass() != o.getClass()) {
    return false;
  }
    PaymentTransactionType paymentTransactionType = (PaymentTransactionType) o;
    return ObjectUtils.equals(this.amount, paymentTransactionType.amount) &&
    ObjectUtils.equals(this.close, paymentTransactionType.close) &&
    ObjectUtils.equals(this.to, paymentTransactionType.to);
  }

  @Override
  public int hashCode() {
    return ObjectUtils.hashCodeMulti(amount, close, to);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class PaymentTransactionType {\n");
    
    sb.append("    amount: ").append(toIndentedString(amount)).append("\n");
    sb.append("    close: ").append(toIndentedString(close)).append("\n");
    sb.append("    to: ").append(toIndentedString(to)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }

}

