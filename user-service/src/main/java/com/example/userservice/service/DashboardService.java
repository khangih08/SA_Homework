package com.example.userservice.service;

import com.example.userservice.client.OrderClient;
import com.example.userservice.dto.dashboard.DashboardOrderDto;
import com.example.userservice.dto.dashboard.DashboardResponse;
import com.example.userservice.dto.dashboard.DashboardUserDto;
import com.example.userservice.exception.ResourceNotFoundException;
import com.example.userservice.model.UserAccount;
import com.example.userservice.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class DashboardService {

    private final UserRepository userRepository;
    private final OrderClient orderClient;

    public DashboardService(UserRepository userRepository, OrderClient orderClient) {
        this.userRepository = userRepository;
        this.orderClient = orderClient;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long currentUserId, String authHeader) {
        UserAccount user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin tài khoản"));

        DashboardUserDto userDto = new DashboardUserDto(
                user.getId(),
                user.getFullName(),
                user.getEmail()
        );

        List<DashboardOrderDto> orders = orderClient.getOrdersForUser(authHeader);

        BigDecimal totalAmount = orders.stream()
                .map(DashboardOrderDto::getTotalAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        return new DashboardResponse(
                userDto,
                orders,
                orders.size(),
                totalAmount
        );
    }
}
