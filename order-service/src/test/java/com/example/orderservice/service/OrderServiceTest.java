package com.example.orderservice.service;

import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.dto.UpdateOrderStatusRequest;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.model.PurchaseOrder;
import com.example.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository);
    }

    @Test
    @DisplayName("Tạo đơn hàng hợp lệ tự động tính totalAmount = quantity * unitPrice")
    void createOrder_Success() {
        CreateOrderRequest request = new CreateOrderRequest("Laptop Asus ROG", 3, new BigDecimal("1500.50"));

        PurchaseOrder savedOrder = new PurchaseOrder(
                100L,
                "Laptop Asus ROG",
                3,
                new BigDecimal("1500.50"),
                new BigDecimal("4501.50"),
                OrderStatus.PENDING
        );
        savedOrder.setId(1L);

        when(orderRepository.save(any(PurchaseOrder.class))).thenReturn(savedOrder);

        OrderResponse response = orderService.createOrder(100L, request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getUserId()).isEqualTo(100L);
        assertThat(response.getProductName()).isEqualTo("Laptop Asus ROG");
        assertThat(response.getQuantity()).isEqualTo(3);
        assertThat(response.getUnitPrice()).isEqualByComparingTo("1500.50");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("4501.50");
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("Tạo đơn hàng với số lượng <= 0 sẽ bị từ chối")
    void createOrder_InvalidQuantity_ThrowsException() {
        CreateOrderRequest request = new CreateOrderRequest("Laptop Asus ROG", 0, new BigDecimal("100.00"));

        assertThatThrownBy(() -> orderService.createOrder(100L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Số lượng sản phẩm phải lớn hơn 0");

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tạo đơn hàng với đơn giá âm sẽ bị từ chối")
    void createOrder_NegativeUnitPrice_ThrowsException() {
        CreateOrderRequest request = new CreateOrderRequest("Laptop Asus ROG", 1, new BigDecimal("-10.00"));

        assertThatThrownBy(() -> orderService.createOrder(100L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Đơn giá sản phẩm không được âm");

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Chủ đơn có thể xem chi tiết đơn hàng của mình")
    void getOrderById_OwnerSuccess() {
        PurchaseOrder order = new PurchaseOrder(100L, "Mouse", 1, new BigDecimal("20.00"), new BigDecimal("20.00"), OrderStatus.PENDING);
        order.setId(5L);

        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrderById(5L, 100L, false);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Người dùng khác xem đơn hàng của người khác bị ném AccessDeniedException (403)")
    void getOrderById_OtherUser_Forbidden() {
        PurchaseOrder order = new PurchaseOrder(100L, "Mouse", 1, new BigDecimal("20.00"), new BigDecimal("20.00"), OrderStatus.PENDING);
        order.setId(5L);

        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrderById(5L, 200L, false))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Bạn không có quyền xem thông tin đơn hàng này");
    }

    @Test
    @DisplayName("Admin có thể xem đơn hàng của bất kỳ người dùng nào")
    void getOrderById_Admin_Success() {
        PurchaseOrder order = new PurchaseOrder(100L, "Mouse", 1, new BigDecimal("20.00"), new BigDecimal("20.00"), OrderStatus.PENDING);
        order.setId(5L);

        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrderById(5L, 999L, true);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Admin có thể cập nhật trạng thái đơn hàng")
    void updateOrderStatus_Admin_Success() {
        PurchaseOrder order = new PurchaseOrder(100L, "Mouse", 1, new BigDecimal("20.00"), new BigDecimal("20.00"), OrderStatus.PENDING);
        order.setId(5L);

        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest(OrderStatus.CONFIRMED);
        OrderResponse response = orderService.updateOrderStatus(5L, request, true);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Người dùng thông thường không được cập nhật trạng thái đơn hàng")
    void updateOrderStatus_NonAdmin_Forbidden() {
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest(OrderStatus.CONFIRMED);

        assertThatThrownBy(() -> orderService.updateOrderStatus(5L, request, false))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Chỉ quản trị viên");

        verify(orderRepository, never()).save(any());
    }
}
