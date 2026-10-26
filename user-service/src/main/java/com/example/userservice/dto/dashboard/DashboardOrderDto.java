package com.example.userservice.dto.dashboard;

import java.math.BigDecimal;

public class DashboardOrderDto {
    private Long id;
    private String productName;
    private Integer quantity;
    private BigDecimal totalAmount;
    private String status;

    public DashboardOrderDto() {
    }

    public DashboardOrderDto(Long id, String productName, Integer quantity, BigDecimal totalAmount, String status) {
        this.id = id;
        this.productName = productName;
        this.quantity = quantity;
        this.totalAmount = totalAmount;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
