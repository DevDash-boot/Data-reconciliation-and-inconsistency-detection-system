package com.example.recon.upload;

import com.example.recon.common.exception.BadRequestException;
import com.example.recon.source.SourceRowWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * CSV 를 파싱해 원천 테이블에 저장한다.
 * v13: JPA saveAll 대신 JDBC batch(SourceRowWriter)로 저장한다.
 */
@Service
@RequiredArgsConstructor
public class UploadService {

    private final UploadBatchRepository batchRepository;
    private final SourceRowWriter rowWriter;

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
        rowWriter.insertPayments(batch.getId(), parsed.valid());
        return UploadResult.of(batch, parsed);
    }

    private UploadResult uploadOrders(String fileName, Reader reader) {
        ParseResult<OrderRecord> parsed = OrderCsvMapper.parse(reader);
        UploadBatch batch = saveBatch(UploadType.ORDER, fileName, parsed);
        rowWriter.insertOrders(batch.getId(), parsed.valid());
        return UploadResult.of(batch, parsed);
    }

    private UploadBatch saveBatch(UploadType type, String fileName, ParseResult<?> parsed) {
        return batchRepository.save(UploadBatch.builder()
                .type(type).fileName(fileName)
                .totalRows(parsed.totalRows()).validRows(parsed.validRows()).errorRows(parsed.errorRows())
                .build());
    }
}
