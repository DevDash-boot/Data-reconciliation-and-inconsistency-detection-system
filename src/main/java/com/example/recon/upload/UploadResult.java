package com.example.recon.upload;

import java.util.List;

/** 업로드 응답. errors 는 최대 50건까지만 담는다. */
public record UploadResult(Long batchId, UploadType type, String fileName,
                           int totalRows, int validRows, int errorRows, List<RowError> errors) {

    private static final int MAX_ERRORS = 50;

    public static UploadResult of(UploadBatch batch, ParseResult<?> parsed) {
        List<RowError> shown = parsed.errors().size() > MAX_ERRORS
                ? parsed.errors().subList(0, MAX_ERRORS) : parsed.errors();
        return new UploadResult(batch.getId(), batch.getType(), batch.getFileName(),
                parsed.totalRows(), parsed.validRows(), parsed.errorRows(), shown);
    }
}
