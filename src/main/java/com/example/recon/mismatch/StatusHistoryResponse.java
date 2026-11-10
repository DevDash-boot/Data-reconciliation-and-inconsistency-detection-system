package com.example.recon.mismatch;

import java.time.LocalDateTime;

public record StatusHistoryResponse(Long id, Long mismatchId, MismatchStatus fromStatus, MismatchStatus toStatus,
                                    String changedBy, String memo, LocalDateTime changedAt) {

    public static StatusHistoryResponse from(MismatchStatusHistory h) {
        return new StatusHistoryResponse(h.getId(), h.getMismatchId(), h.getFromStatus(), h.getToStatus(),
                h.getChangedBy(), h.getMemo(), h.getChangedAt());
    }
}
