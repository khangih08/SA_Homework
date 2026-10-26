package com.example.userservice.service;

import com.example.userservice.dto.UpdateUserRequest;
import com.example.userservice.dto.UserResponse;
import com.example.userservice.exception.ResourceNotFoundException;
import com.example.userservice.model.Role;
import com.example.userservice.model.UserAccount;
import com.example.userservice.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final MinioStorageService minioStorageService;

    public UserService(UserRepository userRepository, MinioStorageService minioStorageService) {
        this.userRepository = userRepository;
        this.minioStorageService = minioStorageService;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long currentUserId) {
        UserAccount user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin tài khoản"));
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public UserResponse updateCurrentUser(Long currentUserId, UpdateUserRequest request) {
        UserAccount user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin tài khoản"));

        user.setFullName(request.getFullName().trim());
        UserAccount updated = userRepository.save(user);
        return UserResponse.fromEntity(updated);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long targetId, Long currentUserId, Role currentRole) {
        if (!targetId.equals(currentUserId) && currentRole != Role.ADMIN) {
            throw new AccessDeniedException("Bạn không có quyền xem thông tin của người dùng khác");
        }

        UserAccount user = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với ID: " + targetId));

        return UserResponse.fromEntity(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public com.example.userservice.dto.avatar.PresignedUrlResponse createAvatarPresignedUrl(
            Long currentUserId,
            com.example.userservice.dto.avatar.PresignedUrlRequest request
    ) {
        // Ensure user exists
        if (!userRepository.existsById(currentUserId)) {
            throw new ResourceNotFoundException("Không tìm thấy thông tin tài khoản");
        }
        return minioStorageService.generatePresignedUploadUrl(currentUserId, request.getFileName(), request.getContentType());
    }

    @Transactional
    public UserResponse confirmAvatar(Long currentUserId, com.example.userservice.dto.avatar.ConfirmAvatarRequest request) {
        UserAccount user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin tài khoản"));

        String avatarUrl = minioStorageService.verifyAndConfirmAvatar(currentUserId, request.getObjectKey());
        user.setAvatarUrl(avatarUrl);
        UserAccount updated = userRepository.save(user);
        return UserResponse.fromEntity(updated);
    }
}
