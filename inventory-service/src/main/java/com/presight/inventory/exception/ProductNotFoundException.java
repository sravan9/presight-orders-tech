package com.presight.inventory.exception;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String productCode) {
        super("Product not found: " + productCode);
    }
}
