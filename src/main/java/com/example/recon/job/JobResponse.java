package com.example.recon.job;

import java.time.LocalDateTime;
import java.util.Map;

public record JobResponse(Long id, Long paymentBatchId, Long orderBatchId, String strategy, String status,
                          LocalDateTime startedAt, LocalDateTime finishedAt,
                          int totalA, int totalB, int excludedA, int excludedB,
                          int missingInA, int missingInB, int duplicate, int valueDiff,
                          String errorMessage, Map<String, Long> timingsMs, boolean reused) {

    public static JobResponse from(ReconciliationJob j, Map<String, Long> timingsMs, boolean reused) {
        return new JobResponse(j.getId(), j.getPaymentBatch().getId(), j.getOrderBatch().getId(),
                j.getStrategy(), j.getStatus().name(), j.getStartedAt(), j.getFinishedAt(),
                j.getTotalA(), j.getTotalB(), j.getExcludedA(), j.getExcludedB(),
                j.getMissingInACount(), j.getMissingInBCount(), j.getDuplicateCount(), j.getValueDiffCount(),
                j.getErrorMessage(), timingsMs, reused);
    }
}
