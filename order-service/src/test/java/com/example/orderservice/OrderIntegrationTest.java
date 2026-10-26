package com.example.orderservice;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.model.PurchaseOrder;
import com.example.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Value("${jwt.secret:TestSuperSecretKeyForJwtAuthenticationMustBeAtLeast256BitsLong!}")
    private String secret;

    @Value("${jwt.issuer:microservices-app}")
    private String issuer;

    @BeforeEach
    void cleanUp() {
        orderRepository.deleteAll();
    }

    private String generateToken(Long userId, String role, long expirySeconds) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expirySeconds);
        Algorithm algorithm = Algorithm.HMAC256(secret);

        return JWT.create()
                .withIssuer(issuer)
                .withSubject(String.valueOf(userId))
                .withClaim("role", role)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiry))
                .sign(algorithm);
    }

    @Test
    @DisplayName("Tạo đơn hàng với JWT hợp lệ tự tính totalAmount và trả về 201")
    void testCreateOrderSuccess() throws Exception {
        String token = generateToken(50L, "USER", 3600);

        String json = """
                {
                    "productName": "Keyboard Keychron K2",
                    "quantity": 2,
                    "unitPrice": 85.00
                }
                """;

        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.userId").value(50))
                .andExpect(jsonPath("$.productName").value("Keyboard Keychron K2"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.unitPrice").value(85.0))
                .andExpect(jsonPath("$.totalAmount").value(170.0))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("Gọi API tạo đơn hàng không có JWT trả về 401")
    void testCreateOrderWithoutToken() throws Exception {
        String json = """
                {
                    "productName": "Keyboard Keychron K2",
                    "quantity": 2,
                    "unitPrice": 85.00
                }
                """;

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Gọi API với JWT giả mạo (sai secret) trả về 401")
    void testCallWithFakeToken() throws Exception {
        Algorithm fakeAlgorithm = Algorithm.HMAC256("completelyWrongSecretKeyForFakeToken1234567890!");
        String fakeToken = JWT.create()
                .withIssuer(issuer)
                .withSubject("50")
                .withClaim("role", "USER")
                .withExpiresAt(new Date(System.currentTimeMillis() + 3600000))
                .sign(fakeAlgorithm);

        mockMvc.perform(get("/orders")
                        .header("Authorization", "Bearer " + fakeToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Token không hợp lệ hoặc đã hết hạn"));
    }

    @Test
    @DisplayName("Gọi API với JWT đã hết hạn trả về 401")
    void testCallWithExpiredToken() throws Exception {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        String expiredToken = JWT.create()
                .withIssuer(issuer)
                .withSubject("50")
                .withClaim("role", "USER")
                .withIssuedAt(new Date(System.currentTimeMillis() - 7200000))
                .withExpiresAt(new Date(System.currentTimeMillis() - 3600000))
                .sign(algorithm);

        mockMvc.perform(get("/orders")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Token không hợp lệ hoặc đã hết hạn"));
    }

    @Test
    @DisplayName("User A không được xem đơn hàng của User B (GET /orders/{id} trả về 403)")
    void testUserACannotViewOrderOfUserB() throws Exception {
        PurchaseOrder orderB = orderRepository.save(new PurchaseOrder(
                100L,
                "Monitor Dell Ultrasharp",
                1,
                new BigDecimal("350.00"),
                new BigDecimal("350.00"),
                OrderStatus.PENDING
        ));

        String tokenUserA = generateToken(200L, "USER", 3600);

        mockMvc.perform(get("/orders/" + orderB.getId())
                        .header("Authorization", "Bearer " + tokenUserA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Bạn không có quyền truy cập tài nguyên này"));
    }

    @Test
    @DisplayName("User A không được xem danh sách đơn của User B (GET /orders/user/{userId} trả về 403)")
    void testUserACannotViewOrdersOfUserBEndpoint() throws Exception {
        String tokenUserA = generateToken(200L, "USER", 3600);

        mockMvc.perform(get("/orders/user/100")
                        .header("Authorization", "Bearer " + tokenUserA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("User thường không được cập nhật trạng thái đơn (PATCH /orders/{id}/status trả về 403)")
    void testNormalUserCannotPatchStatus() throws Exception {
        PurchaseOrder order = orderRepository.save(new PurchaseOrder(
                100L,
                "Monitor Dell",
                1,
                new BigDecimal("350.00"),
                new BigDecimal("350.00"),
                OrderStatus.PENDING
        ));

        String normalUserToken = generateToken(100L, "USER", 3600);

        String patchJson = """
                {
                    "status": "CONFIRMED"
                }
                """;

        mockMvc.perform(patch("/orders/" + order.getId() + "/status")
                        .header("Authorization", "Bearer " + normalUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("ADMIN cập nhật trạng thái đơn thành công (PATCH /orders/{id}/status trả về 200)")
    void testAdminCanPatchStatus() throws Exception {
        PurchaseOrder order = orderRepository.save(new PurchaseOrder(
                100L,
                "Monitor Dell",
                1,
                new BigDecimal("350.00"),
                new BigDecimal("350.00"),
                OrderStatus.PENDING
        ));

        String adminToken = generateToken(999L, "ADMIN", 3600);

        String patchJson = """
                {
                    "status": "CONFIRMED"
                }
                """;

        mockMvc.perform(patch("/orders/" + order.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(order.getId()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }
}
