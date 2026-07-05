package com.presight.inventory.service;

import com.presight.inventory.config.InventoryConfig;
import com.presight.inventory.dto.DeductRequest;
import com.presight.inventory.dto.InventoryResponse;
import com.presight.inventory.entity.Product;
import com.presight.inventory.entity.StockReservation;
import com.presight.inventory.exception.InsufficientStockException;
import com.presight.inventory.exception.ProductNotFoundException;
import com.presight.inventory.repository.ProductRepository;
import com.presight.inventory.repository.StockReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final ProductRepository productRepository;
    private final StockReservationRepository stockReservationRepository;
    private final InventoryConfig inventoryConfig;

    public InventoryService(ProductRepository productRepository,
                            StockReservationRepository stockReservationRepository,
                            InventoryConfig inventoryConfig) {
        this.productRepository = productRepository;
        this.stockReservationRepository = stockReservationRepository;
        this.inventoryConfig = inventoryConfig;
    }

    public List<InventoryResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(product -> new InventoryResponse(
                        product.getProductCode(),
                        product.getName(),
                        product.getStock(),
                        product.getStock() <= inventoryConfig.getLowStockThreshold()))
                .toList();
    }

    public InventoryResponse getInventory(String productCode) {
        Product product = productRepository.findByProductCode(productCode)
                .orElseThrow(() -> new ProductNotFoundException(productCode));

        boolean lowStock = product.getStock() <= inventoryConfig.getLowStockThreshold();
        if (lowStock) {
            log.warn("LOW STOCK WARNING: Product '{}' (code={}) has stock={}, threshold={}",
                    product.getName(), productCode, product.getStock(), inventoryConfig.getLowStockThreshold());
        }

        return new InventoryResponse(product.getProductCode(), product.getName(), product.getStock(), lowStock);
    }

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class,
               maxAttempts = 3, backoff = @Backoff(delay = 50, multiplier = 2))
    @Transactional
    public InventoryResponse deductStock(DeductRequest request) {
        // Idempotency check: if this orderId already deducted, return current state without re-deducting
        if (request.getOrderId() != null) {
            boolean alreadyProcessed = stockReservationRepository
                    .existsByOrderIdAndOperationType(request.getOrderId(), StockReservation.OperationType.DEDUCT);
            if (alreadyProcessed) {
                log.info("Idempotency: deduction for orderId={} already processed, skipping", request.getOrderId());
                Product product = productRepository.findByProductCode(request.getProductCode())
                        .orElseThrow(() -> new ProductNotFoundException(request.getProductCode()));
                return new InventoryResponse(product.getProductCode(), product.getName(), product.getStock(),
                        product.getStock() <= inventoryConfig.getLowStockThreshold());
            }
        }

        Product product = productRepository.findByProductCode(request.getProductCode())
                .orElseThrow(() -> new ProductNotFoundException(request.getProductCode()));

        if (product.getStock() < request.getQuantity()) {
            throw new InsufficientStockException(request.getProductCode(), product.getStock(), request.getQuantity());
        }

        product.setStock(product.getStock() - request.getQuantity());
        product = productRepository.save(product);

        // Record the deduction for idempotency tracking
        if (request.getOrderId() != null) {
            stockReservationRepository.save(new StockReservation(
                    request.getOrderId(), request.getProductCode(), request.getQuantity(),
                    StockReservation.OperationType.DEDUCT));
        }

        boolean lowStock = product.getStock() <= inventoryConfig.getLowStockThreshold();
        if (lowStock) {
            log.warn("LOW STOCK WARNING: Product '{}' (code={}) has stock={} after deduction, threshold={}",
                    product.getName(), request.getProductCode(), product.getStock(), inventoryConfig.getLowStockThreshold());
        }

        return new InventoryResponse(product.getProductCode(), product.getName(), product.getStock(), lowStock);
    }

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class,
               maxAttempts = 3, backoff = @Backoff(delay = 50, multiplier = 2))
    @Transactional
    public InventoryResponse restoreStock(String productCode, int quantity, Long orderId) {
        // Idempotency check: if this orderId already restored, return current state without re-restoring
        if (orderId != null) {
            boolean alreadyProcessed = stockReservationRepository
                    .existsByOrderIdAndOperationType(orderId, StockReservation.OperationType.RESTORE);
            if (alreadyProcessed) {
                log.info("Idempotency: restore for orderId={} already processed, skipping", orderId);
                Product product = productRepository.findByProductCode(productCode)
                        .orElseThrow(() -> new ProductNotFoundException(productCode));
                return new InventoryResponse(product.getProductCode(), product.getName(), product.getStock(),
                        product.getStock() <= inventoryConfig.getLowStockThreshold());
            }
        }

        Product product = productRepository.findByProductCode(productCode)
                .orElseThrow(() -> new ProductNotFoundException(productCode));

        product.setStock(product.getStock() + quantity);
        product = productRepository.save(product);

        // Record the restoration for idempotency tracking
        if (orderId != null) {
            stockReservationRepository.save(new StockReservation(
                    orderId, productCode, quantity, StockReservation.OperationType.RESTORE));
        }

        return new InventoryResponse(product.getProductCode(), product.getName(), product.getStock(),
                product.getStock() <= inventoryConfig.getLowStockThreshold());
    }
}
