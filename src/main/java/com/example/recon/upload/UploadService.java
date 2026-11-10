package com.example.recon.upload;

import com.example.recon.common.exception.BadRequestException;
import com.example.recon.source.SourceOrder;
import com.example.recon.source.SourceOrderRepository;
import com.example.recon.source.SourcePayment;
import com.example.recon.source.SourcePaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * CSV 를 파싱해 원천 테이블에 저장한다.
 * 이 버전은 JPA saveAll 을 쓰는 baseline 이다. (#12 에서 측정, #13 에서 JDBC batch 로 개선)
 */
@Service
@RequiredArgsConstructor
public class UploadService {

    private final UploadBatchRepository batchRepository;
    private final SourcePaymentRepository paymentRepository;
    private final SourceOrderRepository orderRepository;

    @Transactional
    public UploadResult upload(UploadType type, String fileName, InputStream in) {
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return switch (type) {
                case PAYMENT -> uploadPayments(fileName, reader);
                case ORDER -> uploadOrders(fileName, reader);
            };
        } catch (IOException e) {
            throw new BadRequestException("파일을 읽을 수 없습니다: " + e.getMessage());
        }
    }

    private UploadResult uploadPayments(String fileName, Reader reader) {
        ParseResult<PaymentRecord> parsed = PaymentCsvMapper.parse(reader);
        UploadBatch batch = saveBatch(UploadType.PAYMENT, fileName, parsed);

        List<SourcePayment> entities = parsed.valid().stream()
                .map(p -> SourcePayment.builder()
                        .batch(batch).paymentId(p.paymentId()).merchantUid(p.merchantUid())
                        .amount(p.amount()).status(p.status()).paidAt(p.paidAt()).rowNo(p.rowNo())
                        .build())
                .toList();
        paymentRepository.saveAll(entities);
        return UploadResult.of(batch, parsed);
    }

    private UploadResult uploadOrders(String fileName, Reader reader) {
        ParseResult<OrderRecord> parsed = OrderCsvMapper.parse(reader);
        UploadBatch batch = saveBatch(UploadType.ORDER, fileName, parsed);

        List<SourceOrder> entities = parsed.valid().stream()
                .map(o -> SourceOrder.builder()
                        .batch(batch).orderNo(o.orderNo()).totalAmount(o.totalAmount())
                        .status(o.status()).orderedAt(o.orderedAt()).rowNo(o.rowNo())
                        .build())
                .toList();
        orderRepository.saveAll(entities);
        return UploadResult.of(batch, parsed);
    }

    private UploadBatch saveBatch(UploadType type, String fileName, ParseResult<?> parsed) {
        return batchRepository.save(UploadBatch.builder()
                .type(type).fileName(fileName)
                .totalRows(parsed.totalRows()).validRows(parsed.validRows()).errorRows(parsed.errorRows())
                .build());
    }
}
