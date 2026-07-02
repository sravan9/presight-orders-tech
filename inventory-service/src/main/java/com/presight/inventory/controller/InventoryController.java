package com.presight.inventory.controller;

import com.presight.inventory.dto.DeductRequest;
import com.presight.inventory.dto.InventoryResponse;
import com.presight.inventory.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{productCode}")
    public ResponseEntity<InventoryResponse> getInventory(@PathVariable String productCode) {
        return ResponseEntity.ok(inventoryService.getInventory(productCode));
    }

    @PostMapping("/deduct")
    public ResponseEntity<InventoryResponse> deductStock(@RequestBody DeductRequest request) {
        return ResponseEntity.ok(inventoryService.deductStock(request));
    }

    @PostMapping("/restore")
    public ResponseEntity<InventoryResponse> restoreStock(@RequestParam String productCode,
                                                          @RequestParam int quantity,
                                                          @RequestParam(required = false) Long orderId) {
        return ResponseEntity.ok(inventoryService.restoreStock(productCode, quantity, orderId));
    }
}
