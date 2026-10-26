package com.example.userservice.dto;

import com.example.userservice.model.Role;
import com.example.userservice.model.UserAccount;

import java.time.Instant;

public class UserResponse {

    private Long id;
    private String fullName;
    private String email;
    private Role role;
    private Instant createdAt;
    private Instant updatedAt;
    private String avatarUrl;

    public UserResponse() {
    }

    public UserResponse(Long id, String fullName, String email, Role role, Instant createdAt, Instant updatedAt) {
        this(id, fullName, email, role, createdAt, updatedAt, null);
    }

    public UserResponse(Long id, String fullName, String email, Role role, Instant createdAt, Instant updatedAt, String avatarUrl) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.avatarUrl = avatarUrl;
    }

    public static UserResponse fromEntity(UserAccount user) {
        if (user == null) {
            return null;
        }
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getAvatarUrl()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
