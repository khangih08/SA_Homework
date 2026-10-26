package com.example.userservice.dto.avatar;

import jakarta.validation.constraints.NotBlank;

public class ConfirmAvatarRequest {

    @NotBlank(message = "Mã đối tượng (objectKey) không được để trống")
    private String objectKey;

    public ConfirmAvatarRequest() {
    }

    public ConfirmAvatarRequest(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }
}
