package com.example.userservice.dto.avatar;

import jakarta.validation.constraints.NotBlank;

public class PresignedUrlRequest {

    @NotBlank(message = "Tên tệp không được để trống")
    private String fileName;

    @NotBlank(message = "Loại tệp (contentType) không được để trống")
    private String contentType;

    public PresignedUrlRequest() {
    }

    public PresignedUrlRequest(String fileName, String contentType) {
        this.fileName = fileName;
        this.contentType = contentType;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
}
