package com.presight.inventory.dto;

public class DeductRequest {

    private String productCode;
    private int quantity;
    private Long orderId; // Idempotency key: prevents duplicate deductions for the same order

    public DeductRequest() {}

    public DeductRequest(String productCode, int quantity) {
        this(productCode, quantity, null);
    }

    public DeductRequest(String productCode, int quantity, Long orderId) {
        this.productCode = productCode;
        this.quantity = quantity;
        this.orderId = orderId;
    }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
}
