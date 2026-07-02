package com.presight.inventory.repository;

import com.presight.inventory.entity.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {
    boolean existsByOrderIdAndOperationType(Long orderId, StockReservation.OperationType operationType);
}
