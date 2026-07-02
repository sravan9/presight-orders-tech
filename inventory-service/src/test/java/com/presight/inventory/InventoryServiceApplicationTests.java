package com.presight.inventory;

import com.presight.inventory.dto.DeductRequest;
import com.presight.inventory.dto.InventoryResponse;
import com.presight.inventory.exception.InsufficientStockException;
import com.presight.inventory.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class InventoryServiceApplicationTests {

    @Autowired
    private InventoryService inventoryService;

    @Test
    void contextLoads() {
    }

    @Test
    void testGetInventory() {
        InventoryResponse response = inventoryService.getInventory("PROD-001");
        assertNotNull(response);
        assertEquals("PROD-001", response.getProductCode());
        assertEquals("Laptop", response.getName());
        assertTrue(response.getStock() > 0);
    }

    @Test
    void testDeductStock() {
        InventoryResponse before = inventoryService.getInventory("PROD-002");
        int initialStock = before.getStock();

        DeductRequest request = new DeductRequest("PROD-002", 5);
        InventoryResponse response = inventoryService.deductStock(request);

        assertEquals(initialStock - 5, response.getStock());
    }

    @Test
    void testDeductStockInsufficientThrowsException() {
        DeductRequest request = new DeductRequest("PROD-005", 1000);
        assertThrows(InsufficientStockException.class, () -> inventoryService.deductStock(request));
    }

    @Test
    void testLowStockWarning() {
        InventoryResponse response = inventoryService.getInventory("PROD-003");
        assertTrue(response.isLowStock());
    }
}
