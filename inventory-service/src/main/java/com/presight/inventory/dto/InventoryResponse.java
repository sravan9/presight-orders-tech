package com.presight.inventory.dto;

public class InventoryResponse {

    private String productCode;
    private String name;
    private int stock;
    private boolean lowStock;

    public InventoryResponse() {}

    public InventoryResponse(String productCode, String name, int stock, boolean lowStock) {
        this.productCode = productCode;
        this.name = name;
        this.stock = stock;
        this.lowStock = lowStock;
    }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    public boolean isLowStock() { return lowStock; }
    public void setLowStock(boolean lowStock) { this.lowStock = lowStock; }
}
