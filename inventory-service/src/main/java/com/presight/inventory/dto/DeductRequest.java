package com.presight.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class DeductRequest {

    @NotBlank(message = "Product code is required")
    private String productCode;

    @Min(value = 1, message = "Quantity must be at least 1")
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
