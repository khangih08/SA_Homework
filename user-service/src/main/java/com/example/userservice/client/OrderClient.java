package com.example.userservice.client;

import com.example.userservice.dto.dashboard.DashboardOrderDto;
import com.example.userservice.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Component
public class OrderClient {

    private static final Logger log = LoggerFactory.getLogger(OrderClient.class);

    private final RestClient restClient;
    private final String orderServiceUrl;

    public OrderClient(@Value("${order-service.url:http://localhost:8082}") String orderServiceUrl) {
        this.orderServiceUrl = orderServiceUrl;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(orderServiceUrl)
                .build();
    }

    public List<DashboardOrderDto> getOrdersForUser(String authHeader) {
        try {
            List<DashboardOrderDto> orders = restClient.get()
                    .uri("/orders")
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        throw new ServiceUnavailableException("Order Service gặp sự cố nội bộ (" + response.getStatusCode() + ")");
                    })
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        throw new ServiceUnavailableException("Không thể xác thực với Order Service (" + response.getStatusCode() + ")");
                    })
                    .body(new ParameterizedTypeReference<List<DashboardOrderDto>>() {});

            return orders != null ? orders : Collections.emptyList();
        } catch (ServiceUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to connect to Order Service at {}: {}", orderServiceUrl, ex.getMessage());
            throw new ServiceUnavailableException("Không thể kết nối đến Order Service, dịch vụ tạm thời không khả dụng", ex);
        }
    }
}
