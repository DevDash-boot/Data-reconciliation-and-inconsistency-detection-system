package com.example.recon.upload;

import com.example.recon.source.SourceOrderRepository;
import com.example.recon.source.SourcePaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UploadServiceTests {

    @Autowired UploadService uploadService;
    @Autowired UploadBatchRepository batchRepository;
    @Autowired SourcePaymentRepository paymentRepository;
    @Autowired SourceOrderRepository orderRepository;

    private static ByteArrayInputStream bytes(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("결제 CSV 를 업로드하면 정상 행만 저장되고 이력이 남는다")
    void uploadPayments() {
        String csv = "payment_id,merchant_uid,amount,status,paid_at\n"
                + "imp_1,O-1,30000,paid,2026-11-01T10:00:00\n"
                + "imp_2,O-2,xx,paid,2026-11-01T10:00:00\n";

        UploadResult result = uploadService.upload(UploadType.PAYMENT, "p.csv", bytes(csv));

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.validRows()).isEqualTo(1);
        assertThat(result.errorRows()).isEqualTo(1);
        assertThat(paymentRepository.countByBatchId(result.batchId())).isEqualTo(1);
        assertThat(batchRepository.findById(result.batchId()).orElseThrow().getValidRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("주문 CSV 를 업로드하면 주문 테이블에 저장된다")
    void uploadOrders() {
        String csv = "order_no,total_amount,status,ordered_at\n"
                + "O-1,30000,PAID,2026-11-01T10:00:00\n";

        UploadResult result = uploadService.upload(UploadType.ORDER, "o.csv", bytes(csv));

        assertThat(orderRepository.countByBatchId(result.batchId())).isEqualTo(1);
    }
}
