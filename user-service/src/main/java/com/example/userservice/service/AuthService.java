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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException("Email đã tồn tại trong hệ thống: " + normalizedEmail);
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());
        UserAccount newUser = new UserAccount(
                request.getFullName().trim(),
                normalizedEmail,
                encodedPassword,
                Role.USER // Always default to USER on server-side
        );

        UserAccount savedUser = userRepository.save(newUser);
        log.info("Registered new user with id: {}", savedUser.getId());
        return UserResponse.fromEntity(savedUser);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        UserAccount user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Email hoặc mật khẩu không chính xác"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Email hoặc mật khẩu không chính xác");
        }

        String accessToken = jwtService.generateToken(user.getId(), user.getRole());
        log.info("User {} successfully authenticated", user.getId());

        return new AuthResponse(accessToken, "Bearer", jwtService.getExpirationSeconds());
    }

    public com.example.userservice.security.UserPrincipal verifyToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InvalidCredentialsException("Thiếu token xác thực hoặc định dạng không đúng");
        }
        String token = authHeader.substring(7).trim();
        com.auth0.jwt.interfaces.DecodedJWT decoded = jwtService.verifyToken(token);
        Long userId = jwtService.extractUserId(decoded);
        Role role = jwtService.extractRole(decoded);
        return new com.example.userservice.security.UserPrincipal(userId, role);
    }
}
