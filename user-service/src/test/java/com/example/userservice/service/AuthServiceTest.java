package com.example.userservice.service;

import com.example.userservice.dto.AuthResponse;
import com.example.userservice.dto.LoginRequest;
import com.example.userservice.dto.RegisterRequest;
import com.example.userservice.dto.UserResponse;
import com.example.userservice.exception.DuplicateEmailException;
import com.example.userservice.exception.InvalidCredentialsException;
import com.example.userservice.model.Role;
import com.example.userservice.model.UserAccount;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    @DisplayName("Đăng ký tài khoản hợp lệ thành công")
    void register_Success() {
        RegisterRequest request = new RegisterRequest("Nguyen Van A", "vana@gmail.com", "Password@123");

        when(userRepository.existsByEmail("vana@gmail.com")).thenReturn(false);
        when(passwordEncoder.encode("Password@123")).thenReturn("hashedPassword123");

        UserAccount savedUser = new UserAccount("Nguyen Van A", "vana@gmail.com", "hashedPassword123", Role.USER);
        savedUser.setId(1L);
        when(userRepository.save(any(UserAccount.class))).thenReturn(savedUser);

        UserResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("vana@gmail.com");
        assertThat(response.getRole()).isEqualTo(Role.USER);
        verify(userRepository).save(any(UserAccount.class));
    }

    @Test
    @DisplayName("Đăng ký trùng email sẽ ném ngoại lệ DuplicateEmailException")
    void register_DuplicateEmail_ThrowsException() {
        RegisterRequest request = new RegisterRequest("Nguyen Van A", "vana@gmail.com", "Password@123");

        when(userRepository.existsByEmail("vana@gmail.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessageContaining("Email đã tồn tại");

        verify(userRepository, never()).save(any(UserAccount.class));
    }

    @Test
    @DisplayName("Đăng nhập thành công với thông tin chính xác")
    void login_Success() {
        LoginRequest request = new LoginRequest("vana@gmail.com", "Password@123");
        UserAccount user = new UserAccount("Nguyen Van A", "vana@gmail.com", "hashedPassword123", Role.USER);
        user.setId(10L);

        when(userRepository.findByEmail("vana@gmail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password@123", "hashedPassword123")).thenReturn(true);
        when(jwtService.generateToken(10L, Role.USER)).thenReturn("mockJwtToken");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mockJwtToken");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(3600L);
    }

    @Test
    @DisplayName("Đăng nhập sai mật khẩu sẽ ném ngoại lệ InvalidCredentialsException")
    void login_WrongPassword_ThrowsException() {
        LoginRequest request = new LoginRequest("vana@gmail.com", "WrongPassword");
        UserAccount user = new UserAccount("Nguyen Van A", "vana@gmail.com", "hashedPassword123", Role.USER);
        user.setId(10L);

        when(userRepository.findByEmail("vana@gmail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "hashedPassword123")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Email hoặc mật khẩu không chính xác");
    }

    @Test
    @DisplayName("Đăng nhập với email không tồn tại sẽ ném ngoại lệ InvalidCredentialsException")
    void login_EmailNotFound_ThrowsException() {
        LoginRequest request = new LoginRequest("unknown@gmail.com", "Password@123");

        when(userRepository.findByEmail("unknown@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Email hoặc mật khẩu không chính xác");
    }
}
