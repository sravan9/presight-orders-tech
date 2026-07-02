package com.presight.order.exception;

public class InventoryRestoreFailedException extends RuntimeException {
    public InventoryRestoreFailedException(Long orderId, String productCode) {
        super("Failed to restore inventory for order: " + orderId + ", product: " + productCode
                + ". Order cancellation aborted to maintain data consistency.");
    }
}
