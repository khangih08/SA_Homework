package com.example.userservice.service;

import com.example.userservice.dto.avatar.PresignedUrlResponse;
import com.example.userservice.exception.ServiceUnavailableException;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class MinioStorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioStorageService.class);

    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private static final Map<String, String> EXTENSION_MAP = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB

    private final String internalEndpoint;
    private final String publicEndpoint;
    private final String accessKey;
    private final String secretKey;
    private final String bucketName;

    private MinioClient internalMinioClient;
    private MinioClient publicMinioClient;

    public MinioStorageService(
            @Value("${minio.internal-endpoint:http://localhost:9000}") String internalEndpoint,
            @Value("${minio.public-endpoint:http://localhost:9000}") String publicEndpoint,
            @Value("${minio.access-key:minioadmin}") String accessKey,
            @Value("${minio.secret-key:minioadminpassword}") String secretKey,
            @Value("${minio.bucket-name:user-avatars}") String bucketName
    ) {
        this.internalEndpoint = internalEndpoint;
        this.publicEndpoint = publicEndpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.bucketName = bucketName;

        try {
            this.internalMinioClient = MinioClient.builder()
                    .endpoint(internalEndpoint)
                    .credentials(accessKey, secretKey)
                    .region("us-east-1")
                    .build();

            this.publicMinioClient = MinioClient.builder()
                    .endpoint(publicEndpoint)
                    .credentials(accessKey, secretKey)
                    .region("us-east-1")
                    .build();
        } catch (Exception e) {
            log.error("Failed to initialize MinioClient: {}", e.getMessage());
        }
    }

    @PostConstruct
    public void initBucket() {
        if (internalMinioClient == null) {
            return;
        }
        try {
            boolean exists = internalMinioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );
            if (!exists) {
                internalMinioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build()
                );
                log.info("Created MinIO bucket: {}", bucketName);
            } else {
                log.info("MinIO bucket '{}' already exists", bucketName);
            }
        } catch (Exception e) {
            log.warn("MinIO bucket check/creation deferred (MinIO might be starting up): {}", e.getMessage());
        }
    }

    public PresignedUrlResponse generatePresignedUploadUrl(Long userId, String fileName, String contentType) {
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.trim().toLowerCase())) {
            throw new IllegalArgumentException("Loại file không hợp lệ. Chỉ chấp nhận các định dạng ảnh: image/jpeg, image/png, image/webp");
        }

        String ext = EXTENSION_MAP.getOrDefault(contentType.trim().toLowerCase(), ".png");
        String objectKey = String.format("avatars/%d/%s%s", userId, UUID.randomUUID(), ext);

        try {
            String uploadUrl = publicMinioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry(300, TimeUnit.SECONDS)
                            .build()
            );

            log.info("Generated pre-signed upload URL for userId: {}, objectKey: {}", userId, objectKey);
            return new PresignedUrlResponse(uploadUrl, objectKey, 300);
        } catch (Exception e) {
            log.error("Failed to generate presigned URL from MinIO: {}", e.getMessage());
            throw new ServiceUnavailableException("Không thể tạo liên kết upload ảnh, dịch vụ lưu trữ tạm thời không khả dụng", e);
        }
    }

    public String verifyAndConfirmAvatar(Long userId, String objectKey) {
        String expectedPrefix = "avatars/" + userId + "/";
        if (objectKey == null || !objectKey.startsWith(expectedPrefix)) {
            throw new AccessDeniedException("Bạn không có quyền xác nhận ảnh của người dùng khác");
        }

        try {
            StatObjectResponse stat = internalMinioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );

            if (stat.size() > MAX_FILE_SIZE_BYTES) {
                throw new IllegalArgumentException("Dung lượng tệp vượt quá giới hạn tối đa cho phép (5 MB)");
            }

            String publicUrl = String.format("%s/%s/%s", publicEndpoint, bucketName, objectKey);
            log.info("Avatar confirmed for userId: {}, publicUrl: {}", userId, publicUrl);
            return publicUrl;
        } catch (IllegalArgumentException | AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to stat object '{}' in bucket '{}': {}", objectKey, bucketName, e.getMessage());
            throw new IllegalArgumentException("Không tìm thấy tệp ảnh đã upload hoặc tệp chưa sẵn sàng trên hệ thống lưu trữ");
        }
    }
}
