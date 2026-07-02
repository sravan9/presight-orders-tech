package com.presight.order.exception;

public class InventoryDeductionFailedException extends RuntimeException {
    public InventoryDeductionFailedException(String productCode, int quantity) {
        super("Failed to deduct inventory for product: " + productCode + ", quantity: " + quantity);
    }
}
