package com.example.recon.job;

/**
 * Job 실행 요청.
 *  - strategy : EXACT(기본) / AMOUNT_ONLY
 *  - mode     : 실행 방식 (#14 에서 사용, 지금은 무시)
 *  - force    : 동일 Job 이 이미 있을 때 다시 계산할지 (#16 에서 사용, 지금은 무시)
 */
public record JobRequest(Long paymentBatchId, Long orderBatchId, String strategy, String mode, Boolean force) {

    public static JobRequest of(Long paymentBatchId, Long orderBatchId, String strategy) {
        return new JobRequest(paymentBatchId, orderBatchId, strategy, null, null);
    }

    public boolean forceRun() {
        return Boolean.TRUE.equals(force);
    }
}
