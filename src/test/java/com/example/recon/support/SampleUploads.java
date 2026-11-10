package com.example.recon.support;

import com.example.recon.upload.UploadResult;
import com.example.recon.upload.UploadService;
import com.example.recon.upload.UploadType;

/**
 * 테스트 공용 샘플. 업로드하면 대조 결과는 항상 다음과 같다.
 *   MISSING_IN_A 1 (O-5), MISSING_IN_B 1 (O-2), VALUE_DIFF 1 (O-3), DUPLICATE 0
 *   제외: A 1건 (O-4 ready), B 1건 (O-6 PENDING),  대조 대상: A 3건, B 3건
 */
public final class SampleUploads {

    public static final int MISSING_IN_A = 1;
    public static final int MISSING_IN_B = 1;
    public static final int VALUE_DIFF = 1;
    public static final int TOTAL_MISMATCHES = 3;

    public record Batches(Long paymentBatchId, Long orderBatchId) {
    }

    private SampleUploads() {
    }

    public static Batches upload(UploadService uploadService) {
        UploadResult a = uploadService.upload(UploadType.PAYMENT, "a.csv", TestCsv.stream(TestCsv.payments(
                "imp_1,O-1,100,paid,2026-11-01T10:00:00",
                "imp_2,O-2,100,paid,2026-11-01T10:00:00",
                "imp_3,O-3,100,paid,2026-11-01T10:00:00",
                "imp_4,O-4,100,ready,2026-11-01T10:00:00")));
        UploadResult b = uploadService.upload(UploadType.ORDER, "b.csv", TestCsv.stream(TestCsv.orders(
                "O-1,100,PAID,2026-11-01T10:00:00",
                "O-3,90,PAID,2026-11-01T10:00:00",
                "O-5,100,PAID,2026-11-01T10:00:00",
                "O-6,100,PENDING,2026-11-01T10:00:00")));
        return new Batches(a.batchId(), b.batchId());
    }
}
