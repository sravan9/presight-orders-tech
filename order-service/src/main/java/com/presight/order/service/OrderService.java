package com.presight.order.service;

import com.presight.order.client.InventoryClient;
import com.presight.order.dto.CreateOrderRequest;
import com.presight.order.dto.OrderResponse;
import com.presight.order.entity.Order;
import com.presight.order.entity.OrderStatus;
import com.presight.order.exception.InventoryDeductionFailedException;
import com.presight.order.exception.InventoryRestoreFailedException;
import com.presight.order.exception.OrderNotFoundException;
import com.presight.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;

    public OrderService(OrderRepository orderRepository, InventoryClient inventoryClient) {
        this.orderRepository = orderRepository;
        this.inventoryClient = inventoryClient;
    }

    public OrderResponse createOrder(CreateOrderRequest request) {
        // Step 1: Save order as PENDING (persists immediately so we have an orderId for idempotency)
        Order order = new Order(request.getProductCode(), request.getQuantity());
        order = orderRepository.save(order);
        Long orderId = order.getId();

        // Step 2: Attempt inventory deduction via REST
        // The orderId is passed as an idempotency key — if this call is retried (e.g., timeout),
        // inventory service will NOT deduct again for the same orderId.
        boolean deducted = inventoryClient.deductStock(request.getProductCode(), request.getQuantity(), orderId);

        if (!deducted) {
            // Inventory deduction failed — mark as FAILED (no compensation needed)
            order.setStatus(OrderStatus.FAILED);
            orderRepository.save(order);
            log.warn("Order {} failed - inventory deduction failed for product={}, qty={}",
                    orderId, request.getProductCode(), request.getQuantity());
            throw new InventoryDeductionFailedException(request.getProductCode(), request.getQuantity());
        }

        // Step 3: Mark order as CONFIRMED — with compensation if local save fails
        try {
            order.setStatus(OrderStatus.CONFIRMED);
            order = orderRepository.save(order);
            log.info("Order {} confirmed for product={}, qty={}", orderId, request.getProductCode(), request.getQuantity());
        } catch (Exception e) {
            // COMPENSATE: Restore stock since order confirmation failed.
            // The orderId is passed for idempotency — safe to retry.
            log.error("Order {} failed to persist CONFIRMED status, compensating inventory for product={}, qty={}",
                    orderId, request.getProductCode(), request.getQuantity(), e);
            boolean restored = inventoryClient.restoreStock(request.getProductCode(), request.getQuantity(), orderId);

            // FAILURE SCENARIO (double failure):
            // If restoreStock ALSO fails here, we have an inconsistency:
            //   - Inventory: stock is deducted (reservation exists in stock_reservations table)
            //   - Order DB: order is stuck as PENDING (save CONFIRMED failed, save FAILED may also fail)
            //
            // RESOLUTION: A background reconciliation worker should:
            //   1. Query all orders in PENDING status older than X minutes
            //   2. For each, check inventory's stock_reservations table to see if a DEDUCT exists for this orderId
            //   3. If DEDUCT exists but no RESTORE → the stock is reserved but order not confirmed
            //      - Either: confirm the order (if business allows) or restore the stock
            //   4. If neither DEDUCT nor RESTORE exists → order can be safely marked FAILED
            //
            // This is the standard Saga pattern with a "sweeper" job for orphaned state.
            if (!restored) {
                log.error("CRITICAL: Order {} - both confirmation save AND inventory restore failed. "
                        + "Stock is deducted but order is not confirmed. "
                        + "Background reconciliation worker must resolve this.", orderId);
            }

            order.setStatus(OrderStatus.FAILED);
            orderRepository.save(order);
            throw e;
        }

        return toResponse(order);
    }

    public OrderResponse getOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        return toResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        OrderStatus oldStatus = order.getStatus();

        if (oldStatus == OrderStatus.CONFIRMED) {
            // Restore inventory FIRST — only cancel if restore succeeds.
            // The orderId is passed as idempotency key — if this cancel is called twice
            // (e.g., user double-clicks, network retry), inventory will NOT restore twice.
            boolean restored = inventoryClient.restoreStock(order.getProductCode(), order.getQuantity(), id);
            if (!restored) {
                // FAILURE SCENARIO (cancel flow):
                // If restoreStock fails, we do NOT mark the order as cancelled.
                // The order stays CONFIRMED and stock stays deducted — consistent state.
                //
                // RESOLUTION: A background reconciliation worker should:
                //   1. Query orders that have been in CONFIRMED state with a pending cancellation request
                //   2. Retry the restore call to inventory service
                //   3. Once restore succeeds, mark order as CANCELLED
                //
                // Alternatively, the user can retry the cancel action later.
                log.error("Failed to restore stock during cancellation of order {}", id);
                throw new InventoryRestoreFailedException(id, order.getProductCode());
            }
        } else if (oldStatus == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Order " + id + " is already cancelled");
        } else if (oldStatus == OrderStatus.FAILED) {
            throw new IllegalStateException("Order " + id + " has failed and cannot be cancelled");
        } else if (oldStatus == OrderStatus.PENDING) {
            throw new IllegalStateException("Order " + id + " is still pending and cannot be cancelled");
        }

        order.setStatus(OrderStatus.CANCELLED);
        order = orderRepository.save(order);
        log.info("Order {} cancelled (was {})", id, oldStatus);
        return toResponse(order);
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(order.getId(), order.getProductCode(), order.getQuantity(),
                order.getStatus(), order.getCreatedAt(), order.getUpdatedAt());
    }
}
