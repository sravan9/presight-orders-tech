package com.presight.order;

import com.presight.order.client.InventoryClient;
import com.presight.order.dto.CreateOrderRequest;
import com.presight.order.dto.OrderResponse;
import com.presight.order.entity.OrderStatus;
import com.presight.order.exception.InventoryDeductionFailedException;
import com.presight.order.exception.InventoryRestoreFailedException;
import com.presight.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest
class OrderServiceApplicationTests {

    @Autowired
    private OrderService orderService;

    @MockBean
    private InventoryClient inventoryClient;

    @Test
    void contextLoads() {
    }

    @Test
    void testCreateOrderSuccess() {
        when(inventoryClient.deductStock(anyString(), anyInt(), anyLong())).thenReturn(true);

        CreateOrderRequest request = new CreateOrderRequest("PROD-001", 2);
        OrderResponse response = orderService.createOrder(request);

        assertNotNull(response);
        assertEquals("PROD-001", response.getProductCode());
        assertEquals(2, response.getQuantity());
        assertEquals(OrderStatus.CONFIRMED, response.getStatus());
    }

    @Test
    void testCreateOrderFailsWhenInventoryUnavailable() {
        when(inventoryClient.deductStock(anyString(), anyInt(), anyLong())).thenReturn(false);

        CreateOrderRequest request = new CreateOrderRequest("PROD-001", 100);
        assertThrows(InventoryDeductionFailedException.class, () -> orderService.createOrder(request));
    }

    @Test
    void testGetOrder() {
        when(inventoryClient.deductStock(anyString(), anyInt(), anyLong())).thenReturn(true);

        CreateOrderRequest request = new CreateOrderRequest("PROD-002", 1);
        OrderResponse created = orderService.createOrder(request);

        OrderResponse fetched = orderService.getOrder(created.getId());
        assertEquals(created.getId(), fetched.getId());
        assertEquals("PROD-002", fetched.getProductCode());
    }

    @Test
    void testCancelOrder() {
        when(inventoryClient.deductStock(anyString(), anyInt(), anyLong())).thenReturn(true);
        when(inventoryClient.restoreStock(anyString(), anyInt(), anyLong())).thenReturn(true);

        CreateOrderRequest request = new CreateOrderRequest("PROD-003", 1);
        OrderResponse created = orderService.createOrder(request);

        OrderResponse cancelled = orderService.cancelOrder(created.getId());
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void testCancelOrderFailsWhenRestoreFails() {
        when(inventoryClient.deductStock(anyString(), anyInt(), anyLong())).thenReturn(true);
        when(inventoryClient.restoreStock(anyString(), anyInt(), anyLong())).thenReturn(false);

        CreateOrderRequest request = new CreateOrderRequest("PROD-004", 1);
        OrderResponse created = orderService.createOrder(request);
        assertEquals(OrderStatus.CONFIRMED, created.getStatus());

        assertThrows(InventoryRestoreFailedException.class,
                () -> orderService.cancelOrder(created.getId()));

        // Verify order is still CONFIRMED (not cancelled) - consistency maintained
        OrderResponse afterFail = orderService.getOrder(created.getId());
        assertEquals(OrderStatus.CONFIRMED, afterFail.getStatus());
    }
}
