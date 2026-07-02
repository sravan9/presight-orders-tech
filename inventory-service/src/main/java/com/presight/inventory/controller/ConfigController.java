package com.presight.inventory.controller;

import com.presight.inventory.config.InventoryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/inventory/config")
public class ConfigController {

    private static final Logger log = LoggerFactory.getLogger(ConfigController.class);

    private final InventoryConfig inventoryConfig;

    public ConfigController(InventoryConfig inventoryConfig) {
        this.inventoryConfig = inventoryConfig;
    }

    @GetMapping("/threshold")
    public ResponseEntity<Map<String, Object>> getThreshold() {
        return ResponseEntity.ok(Map.of("lowStockThreshold", inventoryConfig.getLowStockThreshold()));
    }

    @PutMapping("/threshold")
    public ResponseEntity<Map<String, Object>> updateThreshold(@RequestParam int value) {
        int oldValue = inventoryConfig.getLowStockThreshold();
        inventoryConfig.setLowStockThreshold(value);
        log.info("Low stock threshold updated dynamically: {} -> {}", oldValue, value);
        return ResponseEntity.ok(Map.of(
                "lowStockThreshold", value,
                "previousValue", oldValue,
                "message", "Threshold updated. In K8s, this is driven by ConfigMap changes."
        ));
    }
}
