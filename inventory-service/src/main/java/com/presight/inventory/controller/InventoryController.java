package com.presight.inventory.controller;

import com.presight.inventory.dto.DeductRequest;
import com.presight.inventory.dto.InventoryResponse;
import com.presight.inventory.service.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@Validated
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public ResponseEntity<List<InventoryResponse>> getAllProducts() {
        return ResponseEntity.ok(inventoryService.getAllProducts());
    }

    @GetMapping("/{productCode}")
    public ResponseEntity<InventoryResponse> getInventory(@PathVariable String productCode) {
        return ResponseEntity.ok(inventoryService.getInventory(productCode));
    }

    @PostMapping("/deduct")
    public ResponseEntity<InventoryResponse> deductStock(@Valid @RequestBody DeductRequest request) {
        return ResponseEntity.ok(inventoryService.deductStock(request));
    }

    @PostMapping("/restore")
    public ResponseEntity<InventoryResponse> restoreStock(@RequestParam String productCode,
                                                          @RequestParam @Min(value = 1, message = "Quantity must be at least 1") int quantity,
                                                          @RequestParam(required = false) Long orderId) {
        return ResponseEntity.ok(inventoryService.restoreStock(productCode, quantity, orderId));
    }
}
