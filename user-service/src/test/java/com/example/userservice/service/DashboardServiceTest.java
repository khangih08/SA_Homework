package com.example.userservice.service;

import com.example.userservice.client.OrderClient;
import com.example.userservice.dto.dashboard.DashboardOrderDto;
import com.example.userservice.dto.dashboard.DashboardResponse;
import com.example.userservice.exception.ServiceUnavailableException;
import com.example.userservice.model.Role;
import com.example.userservice.model.UserAccount;
import com.example.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderClient orderClient;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(userRepository, orderClient);
    }

    @Test
    @DisplayName("Lấy thông tin Dashboard thành công khi có đơn hàng")
    void getDashboard_Success_WithOrders() {
        UserAccount user = new UserAccount("Nguyen Van A", "vana@gmail.com", "hash", Role.USER);
        user.setId(1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        List<DashboardOrderDto> mockOrders = List.of(
                new DashboardOrderDto(10L, "MacBook Pro", 1, new BigDecimal("2000.00"), "CONFIRMED"),
                new DashboardOrderDto(11L, "Mouse Logitech", 2, new BigDecimal("100.50"), "PENDING")
        );
        when(orderClient.getOrdersForUser("Bearer token123")).thenReturn(mockOrders);

        DashboardResponse response = dashboardService.getDashboard(1L, "Bearer token123");

        assertThat(response).isNotNull();
        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getId()).isEqualTo(1L);
        assertThat(response.getUser().getFullName()).isEqualTo("Nguyen Van A");
        assertThat(response.getUser().getEmail()).isEqualTo("vana@gmail.com");

        assertThat(response.getOrders()).hasSize(2);
        assertThat(response.getTotalOrders()).isEqualTo(2);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("2100.50");
    }

    @Test
    @DisplayName("Lấy thông tin Dashboard thành công khi người dùng chưa có đơn hàng nào")
    void getDashboard_Success_NoOrders() {
        UserAccount user = new UserAccount("Nguyen Van B", "vanb@gmail.com", "hash", Role.USER);
        user.setId(2L);

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(orderClient.getOrdersForUser("Bearer token123")).thenReturn(Collections.emptyList());

        DashboardResponse response = dashboardService.getDashboard(2L, "Bearer token123");

        assertThat(response).isNotNull();
        assertThat(response.getTotalOrders()).isEqualTo(0);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("0.00");
        assertThat(response.getOrders()).isEmpty();
    }

    @Test
    @DisplayName("Order Service không khả dụng sẽ ném ngoại lệ ServiceUnavailableException (503)")
    void getDashboard_OrderServiceUnavailable_ThrowsException() {
        UserAccount user = new UserAccount("Nguyen Van C", "vanc@gmail.com", "hash", Role.USER);
        user.setId(3L);

        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(orderClient.getOrdersForUser("Bearer token123"))
                .thenThrow(new ServiceUnavailableException("Không thể kết nối đến Order Service"));

        assertThatThrownBy(() -> dashboardService.getDashboard(3L, "Bearer token123"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Không thể kết nối đến Order Service");
    }
}
