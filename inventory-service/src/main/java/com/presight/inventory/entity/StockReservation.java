package com.presight.inventory.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "stock_reservations", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"orderId", "operationType"})
})
public class StockReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;

    private String productCode;

    private int quantity;

    @Enumerated(EnumType.STRING)
    private OperationType operationType;

    public enum OperationType {
        DEDUCT,
        RESTORE
    }

    public StockReservation() {}

    public StockReservation(Long orderId, String productCode, int quantity, OperationType operationType) {
        this.orderId = orderId;
        this.productCode = productCode;
        this.quantity = quantity;
        this.operationType = operationType;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public String getProductCode() { return productCode; }
    public int getQuantity() { return quantity; }
    public OperationType getOperationType() { return operationType; }
}
