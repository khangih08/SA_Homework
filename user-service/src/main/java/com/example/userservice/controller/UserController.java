package com.example.userservice.controller;

import com.example.userservice.dto.UpdateUserRequest;
import com.example.userservice.dto.UserResponse;
import com.example.userservice.security.UserPrincipal;
import com.example.userservice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@AuthenticationPrincipal UserPrincipal principal) {
        UserResponse response = userService.getCurrentUser(principal.getUserId());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateMe(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        UserResponse response = userService.updateCurrentUser(principal.getUserId(), request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/me/avatar/presign")
    public ResponseEntity<com.example.userservice.dto.avatar.PresignedUrlResponse> presignAvatar(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody com.example.userservice.dto.avatar.PresignedUrlRequest request
    ) {
        com.example.userservice.dto.avatar.PresignedUrlResponse response =
                userService.createAvatarPresignedUrl(principal.getUserId(), request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/me/avatar/confirm")
    public ResponseEntity<UserResponse> confirmAvatar(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody com.example.userservice.dto.avatar.ConfirmAvatarRequest request
    ) {
        UserResponse response = userService.confirmAvatar(principal.getUserId(), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UserResponse response = userService.getUserById(id, principal.getUserId(), principal.getRole());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> response = userService.getAllUsers();
        return ResponseEntity.ok(response);
    }
}
