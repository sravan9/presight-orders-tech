package com.presight.order.dto;

public class CreateOrderRequest {

    private String productCode;
    private int quantity;

    public CreateOrderRequest() {}

    public CreateOrderRequest(String productCode, int quantity) {
        this.productCode = productCode;
        this.quantity = quantity;
    }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
