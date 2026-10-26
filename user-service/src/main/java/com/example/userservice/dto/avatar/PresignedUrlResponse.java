package com.example.userservice.dto.avatar;

public class PresignedUrlResponse {

    private String uploadUrl;
    private String objectKey;
    private int expiresIn = 300;

    public PresignedUrlResponse() {
    }

    public PresignedUrlResponse(String uploadUrl, String objectKey, int expiresIn) {
        this.uploadUrl = uploadUrl;
        this.objectKey = objectKey;
        this.expiresIn = expiresIn;
    }

    public String getUploadUrl() {
        return uploadUrl;
    }

    public void setUploadUrl(String uploadUrl) {
        this.uploadUrl = uploadUrl;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public int getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(int expiresIn) {
        this.expiresIn = expiresIn;
    }
}
