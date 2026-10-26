package com.example.orderservice.service;

import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.dto.UpdateOrderStatusRequest;
import com.example.orderservice.exception.ResourceNotFoundException;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.model.PurchaseOrder;
import com.example.orderservice.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponse createOrder(Long currentUserId, CreateOrderRequest request) {
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new IllegalArgumentException("Số lượng sản phẩm phải lớn hơn 0");
        }
        if (request.getUnitPrice() == null || request.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Đơn giá sản phẩm không được âm");
        }

        BigDecimal calculatedTotal = request.getUnitPrice()
                .multiply(BigDecimal.valueOf(request.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);

        PurchaseOrder order = new PurchaseOrder(
                currentUserId,
                request.getProductName().trim(),
                request.getQuantity(),
                request.getUnitPrice().setScale(2, RoundingMode.HALF_UP),
                calculatedTotal,
                OrderStatus.PENDING
        );

        PurchaseOrder savedOrder = orderRepository.save(order);
        log.info("Created order {} for userId {}", savedOrder.getId(), currentUserId);
        return OrderResponse.fromEntity(savedOrder);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersForCurrentUser(Long currentUserId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(currentUserId).stream()
                .map(OrderResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId, Long currentUserId, boolean isAdmin) {
        PurchaseOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với ID: " + orderId));

        if (!order.getUserId().equals(currentUserId) && !isAdmin) {
            throw new AccessDeniedException("Bạn không có quyền xem thông tin đơn hàng này");
        }

        return OrderResponse.fromEntity(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByUser(Long targetUserId, Long currentUserId, boolean isAdmin) {
        if (!targetUserId.equals(currentUserId) && !isAdmin) {
            throw new AccessDeniedException("Bạn không có quyền truy cập đơn hàng của người dùng khác");
        }

        return orderRepository.findByUserIdOrderByCreatedAtDesc(targetUserId).stream()
                .map(OrderResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, UpdateOrderStatusRequest request, boolean isAdmin) {
        if (!isAdmin) {
            throw new AccessDeniedException("Chỉ quản trị viên mới có quyền cập nhật trạng thái đơn hàng");
        }

        PurchaseOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với ID: " + orderId));

        order.setStatus(request.getStatus());
        PurchaseOrder updatedOrder = orderRepository.save(order);
        log.info("Updated order {} status to {}", orderId, request.getStatus());
        return OrderResponse.fromEntity(updatedOrder);
    }
}
