package com.example.orderservice.repository;

import com.example.orderservice.model.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<PurchaseOrder> findAllByOrderByCreatedAtDesc();
}
