package com.example.recon.source;

import com.example.recon.upload.OrderRecord;
import com.example.recon.upload.PaymentRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;

/**
 * 원천 행을 JDBC batch 로 한꺼번에 INSERT 한다.
 *
 * JPA saveAll 이 느린 이유: PK 가 IDENTITY 라서 행마다 INSERT 가 하나씩 나간다.
 * 여기서는 batchUpdate + rewriteBatchedStatements=true 로 여러 행을 한 번에 보낸다.
 * (같은 트랜잭션 안에서 호출하면 JPA 와 같은 커넥션/트랜잭션을 공유한다)
 */
@Component
public class SourceRowWriter {

    private static final String PAYMENT_SQL =
            "INSERT INTO source_payment (batch_id, payment_id, merchant_uid, amount, status, paid_at, row_no) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String ORDER_SQL =
            "INSERT INTO source_order (batch_id, order_no, total_amount, status, ordered_at, row_no) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbc;
    private final int batchSize;

    public SourceRowWriter(JdbcTemplate jdbc, @Value("${recon.jdbc.batch-size:1000}") int batchSize) {
        this.jdbc = jdbc;
        this.batchSize = batchSize;
    }

    public void insertPayments(long batchId, List<PaymentRecord> rows) {
        jdbc.batchUpdate(PAYMENT_SQL, rows, batchSize, (ps, p) -> {
            ps.setLong(1, batchId);
            ps.setString(2, p.paymentId());
            ps.setString(3, p.merchantUid());
            ps.setLong(4, p.amount());
            ps.setString(5, p.status());
            ps.setTimestamp(6, Timestamp.valueOf(p.paidAt()));
            ps.setInt(7, p.rowNo());
        });
    }

    public void insertOrders(long batchId, List<OrderRecord> rows) {
        jdbc.batchUpdate(ORDER_SQL, rows, batchSize, (ps, o) -> {
            ps.setLong(1, batchId);
            ps.setString(2, o.orderNo());
            ps.setLong(3, o.totalAmount());
            ps.setString(4, o.status());
            ps.setTimestamp(5, Timestamp.valueOf(o.orderedAt()));
            ps.setInt(6, o.rowNo());
        });
    }
}
