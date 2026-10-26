package com.example.userservice.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

public class DashboardResponse {
    private DashboardUserDto user;
    private List<DashboardOrderDto> orders;
    private Integer totalOrders;
    private BigDecimal totalAmount;

    public DashboardResponse() {
    }

    public DashboardResponse(DashboardUserDto user, List<DashboardOrderDto> orders, Integer totalOrders, BigDecimal totalAmount) {
        this.user = user;
        this.orders = orders;
        this.totalOrders = totalOrders;
        this.totalAmount = totalAmount;
    }

    public DashboardUserDto getUser() {
        return user;
    }

    public void setUser(DashboardUserDto user) {
        this.user = user;
    }

    public List<DashboardOrderDto> getOrders() {
        return orders;
    }

    public void setOrders(List<DashboardOrderDto> orders) {
        this.orders = orders;
    }

    public Integer getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(Integer totalOrders) {
        this.totalOrders = totalOrders;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
}
