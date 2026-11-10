package com.example.recon.mismatch;

import java.time.LocalDateTime;

public record MismatchResponse(Long id, Long jobId, String matchKey, MismatchType type, MismatchStatus status,
                               String diffDetail, String resolvedBy, LocalDateTime resolvedAt,
                               String memo, LocalDateTime createdAt) {

    public static MismatchResponse from(Mismatch m) {
        return new MismatchResponse(m.getId(), m.getJob().getId(), m.getMatchKey(), m.getType(), m.getStatus(),
                m.getDiffDetail(), m.getResolvedBy(), m.getResolvedAt(), m.getMemo(), m.getCreatedAt());
    }
}
