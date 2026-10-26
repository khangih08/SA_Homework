package com.example.userservice;

import com.example.userservice.model.Role;
import com.example.userservice.model.UserAccount;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UserIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("API POST /auth/register đăng ký tài khoản thành công trả về 201")
    void testRegisterSuccess() throws Exception {
        String json = """
                {
                    "fullName": "Nguyen Van A",
                    "email": "vana@gmail.com",
                    "password": "Password@123"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.fullName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.email").value("vana@gmail.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("API POST /auth/register đăng ký trùng email trả về 409")
    void testRegisterDuplicateEmail() throws Exception {
        UserAccount existingUser = new UserAccount("Old User", "duplicate@gmail.com", passwordEncoder.encode("123456"), Role.USER);
        userRepository.save(existingUser);

        String json = """
                {
                    "fullName": "New User",
                    "email": "duplicate@gmail.com",
                    "password": "Password@123"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("Email đã tồn tại")));
    }

    @Test
    @DisplayName("API POST /auth/login đúng thông tin trả về 200 và JWT token")
    void testLoginSuccess() throws Exception {
        UserAccount user = new UserAccount("Test User", "testlogin@gmail.com", passwordEncoder.encode("Password@123"), Role.USER);
        userRepository.save(user);

        String json = """
                {
                    "email": "testlogin@gmail.com",
                    "password": "Password@123"
                }
                """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    @DisplayName("API POST /auth/login sai mật khẩu trả về 401")
    void testLoginWrongPassword() throws Exception {
        UserAccount user = new UserAccount("Test User", "wrongpass@gmail.com", passwordEncoder.encode("Password@123"), Role.USER);
        userRepository.save(user);

        String json = """
                {
                    "email": "wrongpass@gmail.com",
                    "password": "WrongPassword!"
                }
                """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Email hoặc mật khẩu không chính xác"));
    }

    @Test
    @DisplayName("API GET /users/me không có JWT trả về 401")
    void testGetMeWithoutToken() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("API GET /users/me với JWT hợp lệ trả về 200 và thông tin cá nhân")
    void testGetMeWithValidToken() throws Exception {
        UserAccount user = new UserAccount("Nguyen Van B", "vanb@gmail.com", passwordEncoder.encode("Password@123"), Role.USER);
        UserAccount saved = userRepository.save(user);

        String token = jwtService.generateToken(saved.getId(), saved.getRole());

        mockMvc.perform(get("/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.fullName").value("Nguyen Van B"))
                .andExpect(jsonPath("$.email").value("vanb@gmail.com"));
    }

    @Test
    @DisplayName("API PUT /users/me cập nhật họ tên cá nhân thành công")
    void testUpdateMe() throws Exception {
        UserAccount user = new UserAccount("Old Name", "updateme@gmail.com", passwordEncoder.encode("Password@123"), Role.USER);
        UserAccount saved = userRepository.save(user);

        String token = jwtService.generateToken(saved.getId(), saved.getRole());

        String json = """
                {
                    "fullName": "New Updated Name"
                }
                """;

        mockMvc.perform(put("/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.fullName").value("New Updated Name"));
    }

    @Test
    @DisplayName("User thường không được xem thông tin User khác (GET /users/{id} trả về 403)")
    void testUserCannotViewOtherUser() throws Exception {
        UserAccount user1 = userRepository.save(new UserAccount("User One", "user1@gmail.com", passwordEncoder.encode("123456"), Role.USER));
        UserAccount user2 = userRepository.save(new UserAccount("User Two", "user2@gmail.com", passwordEncoder.encode("123456"), Role.USER));

        String tokenUser1 = jwtService.generateToken(user1.getId(), user1.getRole());

        mockMvc.perform(get("/users/" + user2.getId())
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("User thường không thể gọi GET /users (trả về 403)")
    void testUserCannotListAllUsers() throws Exception {
        UserAccount user = userRepository.save(new UserAccount("User Normal", "normal@gmail.com", passwordEncoder.encode("123456"), Role.USER));
        String token = jwtService.generateToken(user.getId(), user.getRole());

        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("ADMIN có thể xem danh sách tất cả người dùng (GET /users trả về 200)")
    void testAdminCanListAllUsers() throws Exception {
        UserAccount admin = userRepository.save(new UserAccount("Admin System", "admin@gmail.com", passwordEncoder.encode("123456"), Role.ADMIN));
        String adminToken = jwtService.generateToken(admin.getId(), admin.getRole());

        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("API GET /auth/verify với JWT hợp lệ trả về 200 và headers nhận diện")
    void testVerifyValidToken() throws Exception {
        UserAccount user = userRepository.save(new UserAccount("Verify User", "verify@gmail.com", passwordEncoder.encode("123456"), Role.USER));
        String token = jwtService.generateToken(user.getId(), user.getRole());

        mockMvc.perform(get("/auth/verify")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("X-User-Id", String.valueOf(user.getId())))
                .andExpect(header().string("X-User-Role", "USER"));
    }

    @Test
    @DisplayName("API GET /auth/verify không có JWT trả về 401")
    void testVerifyMissingToken() throws Exception {
        mockMvc.perform(get("/auth/verify"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("API POST /users/me/avatar/presign với JWT hợp lệ trả về URL có hạn 300s")
    void testPresignAvatar() throws Exception {
        UserAccount user = userRepository.save(new UserAccount("Avatar User", "avatar@gmail.com", passwordEncoder.encode("123456"), Role.USER));
        String token = jwtService.generateToken(user.getId(), user.getRole());

        String json = """
                {
                    "fileName": "photo.png",
                    "contentType": "image/png"
                }
                """;

        mockMvc.perform(post("/users/me/avatar/presign")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").isNotEmpty())
                .andExpect(jsonPath("$.objectKey").value(startsWith("avatars/" + user.getId() + "/")))
                .andExpect(jsonPath("$.expiresIn").value(300));
    }
}
