package com.example.recon.upload;

import java.time.LocalDateTime;

public record UploadBatchResponse(Long id, UploadType type, String fileName,
                                  int totalRows, int validRows, int errorRows, LocalDateTime createdAt) {

    public static UploadBatchResponse from(UploadBatch b) {
        return new UploadBatchResponse(b.getId(), b.getType(), b.getFileName(),
                b.getTotalRows(), b.getValidRows(), b.getErrorRows(), b.getCreatedAt());
    }
}
