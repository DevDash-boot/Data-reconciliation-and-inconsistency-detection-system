package com.example.recon.mismatch;

import com.example.recon.matching.MismatchItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/** 불일치 결과를 JDBC batch 로 저장한다. (JPA 의 @CreationTimestamp 가 없으므로 created_at 을 직접 넣는다) */
@Component
public class MismatchBatchWriter {

    private static final String SQL =
            "INSERT INTO mismatch (job_id, match_key, type, status, diff_detail, created_at) VALUES (?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbc;
    private final int batchSize;

    public MismatchBatchWriter(JdbcTemplate jdbc, @Value("${recon.jdbc.batch-size:1000}") int batchSize) {
        this.jdbc = jdbc;
        this.batchSize = batchSize;
    }

    public void insertAll(long jobId, List<MismatchItem> items) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbc.batchUpdate(SQL, items, batchSize, (ps, it) -> {
            ps.setLong(1, jobId);
            ps.setString(2, it.key());
            ps.setString(3, it.type().name());
            ps.setString(4, MismatchStatus.OPEN.name());
            ps.setString(5, it.detail());
            ps.setTimestamp(6, now);
        });
    }
}
