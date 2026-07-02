package com.presight.inventory.exception;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String productCode, int available, int requested) {
        super("Insufficient stock for product: " + productCode
                + ". Available: " + available + ", Requested: " + requested);
    }
}
