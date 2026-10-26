package com.example.userservice.service;

import com.example.userservice.dto.avatar.PresignedUrlResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MinioStorageServiceTest {

    private MinioStorageService minioStorageService;

    @BeforeEach
    void setUp() {
        minioStorageService = new MinioStorageService(
                "http://localhost:9000",
                "http://localhost:9000",
                "minioadmin",
                "minioadminpassword",
                "user-avatars"
        );
    }

    @Test
    @DisplayName("Tạo pre-signed URL với định dạng ảnh hợp lệ")
    void generatePresignedUploadUrl_Success() {
        PresignedUrlResponse response = minioStorageService.generatePresignedUploadUrl(1L, "avatar.png", "image/png");

        assertThat(response).isNotNull();
        assertThat(response.getUploadUrl()).isNotEmpty();
        assertThat(response.getUploadUrl()).contains("user-avatars/avatars/1/");
        assertThat(response.getObjectKey()).startsWith("avatars/1/");
        assertThat(response.getObjectKey()).endsWith(".png");
        assertThat(response.getExpiresIn()).isEqualTo(300);
    }

    @Test
    @DisplayName("Tạo pre-signed URL với định dạng không phải ảnh sẽ bị từ chối (HTTP 400)")
    void generatePresignedUploadUrl_InvalidContentType() {
        assertThatThrownBy(() -> minioStorageService.generatePresignedUploadUrl(1L, "document.pdf", "application/pdf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Loại file không hợp lệ");
    }

    @Test
    @DisplayName("Xác nhận ảnh với objectKey của user khác sẽ bị từ chối (HTTP 403)")
    void verifyAndConfirmAvatar_OtherUserObjectKey_Forbidden() {
        assertThatThrownBy(() -> minioStorageService.verifyAndConfirmAvatar(1L, "avatars/2/hacked.png"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Bạn không có quyền xác nhận ảnh của người dùng khác");
    }
}
