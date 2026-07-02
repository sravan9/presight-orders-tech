package com.presight.inventory.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InventoryConfig {

    private static final Logger log = LoggerFactory.getLogger(InventoryConfig.class);

    @Value("${inventory.low-stock-threshold:10}")
    private int lowStockThreshold;

    @PostConstruct
    public void init() {
        log.info("Inventory low-stock-threshold initialized to: {}", lowStockThreshold);
    }

    public int getLowStockThreshold() {
        return lowStockThreshold;
    }

    public void setLowStockThreshold(int lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }
}
