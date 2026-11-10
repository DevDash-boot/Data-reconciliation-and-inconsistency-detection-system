package com.example.recon.job;

import com.example.recon.matching.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 대조에 필요한 세 컬럼(키, 금액, 상태)만 JDBC 로 읽는다.
 * JPA 엔티티로 읽는 것보다 객체 생성이 훨씬 적다.
 */
@Component
@RequiredArgsConstructor
public class RowLoader {

    private static final String PAYMENTS =
            "SELECT merchant_uid, amount, status FROM source_payment WHERE batch_id = ?";
    private static final String ORDERS =
            "SELECT order_no, total_amount, status FROM source_order WHERE batch_id = ?";

    private final JdbcTemplate jdbc;

    public List<Row> loadPayments(long batchId) {
        return jdbc.query(PAYMENTS, (rs, i) -> new Row(rs.getString(1), rs.getLong(2), rs.getString(3)), batchId);
    }

    public List<Row> loadOrders(long batchId) {
        return jdbc.query(ORDERS, (rs, i) -> new Row(rs.getString(1), rs.getLong(2), rs.getString(3)), batchId);
    }

    /** 해시 파티션 하나만 읽는다. HashPartitioner.partition(key, partitions) == partition 인 행들. */
    public List<Row> loadPayments(long batchId, int partitions, int partition) {
        return jdbc.query(PAYMENTS + " AND MOD(CRC32(merchant_uid), ?) = ?",
                (rs, i) -> new Row(rs.getString(1), rs.getLong(2), rs.getString(3)),
                batchId, partitions, partition);
    }

    public List<Row> loadOrders(long batchId, int partitions, int partition) {
        return jdbc.query(ORDERS + " AND MOD(CRC32(order_no), ?) = ?",
                (rs, i) -> new Row(rs.getString(1), rs.getLong(2), rs.getString(3)),
                batchId, partitions, partition);
    }

    /** CRC32() 함수를 쓸 수 있는 DB(MySQL, MariaDB)인지. 테스트용 H2 는 지원하지 않는다. */
    public boolean supportsHashPartition() {
        String product = jdbc.execute((ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
        return product != null && (product.contains("MySQL") || product.contains("MariaDB"));
    }
}
