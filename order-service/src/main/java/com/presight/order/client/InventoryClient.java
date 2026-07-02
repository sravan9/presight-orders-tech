package com.presight.order.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class InventoryClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryClient.class);

    private final RestTemplate restTemplate;

    @Value("${inventory.service.url}")
    private String inventoryServiceUrl;

    public InventoryClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean deductStock(String productCode, int quantity, Long orderId) {
        try {
            String url = inventoryServiceUrl + "/api/inventory/deduct";
            Map<String, Object> request = Map.of(
                    "productCode", productCode,
                    "quantity", quantity,
                    "orderId", orderId);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Failed to deduct stock for product={}, quantity={}, orderId={}: {}",
                    productCode, quantity, orderId, e.getMessage());
            return false;
        }
    }

    public boolean restoreStock(String productCode, int quantity, Long orderId) {
        try {
            String url = inventoryServiceUrl + "/api/inventory/restore?productCode=" + productCode
                    + "&quantity=" + quantity + "&orderId=" + orderId;
            ResponseEntity<Map> response = restTemplate.postForEntity(url, null, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Failed to restore stock for product={}, quantity={}, orderId={}: {}",
                    productCode, quantity, orderId, e.getMessage());
            return false;
        }
    }
}
