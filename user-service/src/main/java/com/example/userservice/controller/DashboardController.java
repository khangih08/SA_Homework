package com.example.userservice.controller;

import com.example.userservice.dto.dashboard.DashboardResponse;
import com.example.userservice.security.UserPrincipal;
import com.example.userservice.service.DashboardService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/me")
    public ResponseEntity<DashboardResponse> getMyDashboard(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
    ) {
        DashboardResponse response = dashboardService.getDashboard(principal.getUserId(), authHeader);
        return ResponseEntity.ok(response);
    }
}
