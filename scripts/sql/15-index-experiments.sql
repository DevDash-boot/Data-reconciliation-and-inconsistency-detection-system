-- =====================================================================
-- #15 인덱스 실험 (MySQL 8.0.18+ : EXPLAIN ANALYZE 사용)
--
-- 준비
--   1) PerfRunner 로 100k 또는 1m 데이터를 한 번 돌려 둔다. (마지막 실행 데이터가 DB 에 남는다)
--   2) 이 파일을 한 구간씩 실행하고, 결과(type / key / rows / Extra / 시간)를 Wiki 7번 "EXPLAIN 기록" 에 적는다.
--      실행 예) docker exec -i recon-mysql mysql -u recon -p recon < scripts/sql/15-index-experiments.sql
--
-- EXPLAIN 읽는 법
--   type  : ALL(풀스캔) < index < range < ref < eq_ref  (오른쪽일수록 좋음)
--   key   : 실제로 쓴 인덱스 (NULL 이면 못 씀)
--   rows  : 훑을 것으로 예상하는 행 수 (작을수록 좋음)
--   Extra : "Using index"(커버링, 테이블 안 읽음) 좋음 / "Using filesort", "Using temporary" 는 비용 신호
-- =====================================================================

SET @pb  = (SELECT id FROM upload_batch WHERE type = 'PAYMENT' ORDER BY id DESC LIMIT 1);
SET @ob  = (SELECT id FROM upload_batch WHERE type = 'ORDER'   ORDER BY id DESC LIMIT 1);
SET @job = (SELECT id FROM reconciliation_job ORDER BY id DESC LIMIT 1);
SELECT @pb AS payment_batch, @ob AS order_batch, @job AS job;

SHOW INDEX FROM source_payment;
SHOW INDEX FROM mismatch;

-- ---------------------------------------------------------------------
-- [A] 적용 전 (현재 인덱스) -- 결과를 기록한다
-- ---------------------------------------------------------------------

-- Q1. IN_MEMORY 로드 (RowLoader.loadPayments)
EXPLAIN SELECT merchant_uid, amount, status FROM source_payment WHERE batch_id = @pb;
EXPLAIN ANALYZE SELECT merchant_uid, amount, status FROM source_payment WHERE batch_id = @pb;

-- Q2. CHUNKED 로드 (파티션 3번, 16개 청크)
EXPLAIN SELECT merchant_uid, amount, status FROM source_payment
 WHERE batch_id = @pb AND MOD(CRC32(merchant_uid), 16) = 3;
EXPLAIN ANALYZE SELECT merchant_uid, amount, status FROM source_payment
 WHERE batch_id = @pb AND MOD(CRC32(merchant_uid), 16) = 3;

-- Q3. 불일치 목록 (유형 필터 + 최신순 페이지)  ->  #18 화면의 핵심 쿼리
EXPLAIN SELECT * FROM mismatch WHERE job_id = @job AND type = 'VALUE_DIFF' ORDER BY id DESC LIMIT 20;

-- Q4. 상태별 건수
EXPLAIN SELECT COUNT(*) FROM mismatch WHERE job_id = @job AND status = 'OPEN';

-- Q5. 대시보드: Job 별 유형 집계
EXPLAIN SELECT type, COUNT(*) FROM mismatch WHERE job_id = @job GROUP BY type;

-- Q6. 대시보드: 전체 상태 집계
EXPLAIN SELECT status, COUNT(*) FROM mismatch GROUP BY status;

-- Q7. 대시보드: 일별 추이 (함수를 씌우면 created_at 인덱스를 못 쓴다)
EXPLAIN SELECT CAST(created_at AS DATE) AS d, COUNT(*) FROM mismatch GROUP BY d ORDER BY d DESC LIMIT 14;

-- ---------------------------------------------------------------------
-- [B] 실험 1: 커버링 인덱스 (로드 쿼리가 테이블 본문을 안 읽게)
--     가설: (batch_id, 키, amount, status) 를 인덱스에 모두 넣으면 Extra 에 "Using index" 가 뜨고 로드가 빨라진다.
--     대가: 인덱스가 커지고 INSERT 가 느려진다 -> 아래 [D] 에서 쓰기 비용도 반드시 측정한다.
-- ---------------------------------------------------------------------
ALTER TABLE source_payment ADD INDEX idx_payment_cover (batch_id, merchant_uid, amount, status);
ALTER TABLE source_order   ADD INDEX idx_order_cover   (batch_id, order_no, total_amount, status);
ANALYZE TABLE source_payment, source_order;

-- Q1, Q2 를 다시 실행하고 [A] 와 비교한다
EXPLAIN SELECT merchant_uid, amount, status FROM source_payment WHERE batch_id = @pb;
EXPLAIN ANALYZE SELECT merchant_uid, amount, status FROM source_payment WHERE batch_id = @pb;
EXPLAIN SELECT merchant_uid, amount, status FROM source_payment
 WHERE batch_id = @pb AND MOD(CRC32(merchant_uid), 16) = 3;

-- ---------------------------------------------------------------------
-- [C] 실험 2: 대시보드 일별 추이 -- 함수 대신 범위 조건 + created_at 인덱스
-- ---------------------------------------------------------------------
ALTER TABLE mismatch ADD INDEX idx_mismatch_created (created_at);
ANALYZE TABLE mismatch;

-- 최근 14일 범위로 바꾸면 인덱스를 탈 수 있다
EXPLAIN SELECT DATE(created_at) AS d, COUNT(*) FROM mismatch
 WHERE created_at >= (NOW() - INTERVAL 14 DAY) GROUP BY d;

-- ---------------------------------------------------------------------
-- [D] 쓰기 비용 측정 (인덱스를 추가하면 INSERT 가 얼마나 느려지나)
--     1) 위 인덱스가 있는 상태에서 PerfRunner 를 돌리고 "업로드 적재" 시간을 기록
--     2) 아래로 인덱스를 지우고 같은 조건으로 다시 돌려 비교
--     읽기 이득 > 쓰기 손해 일 때만 적용한다.
-- ---------------------------------------------------------------------

-- ---------------------------------------------------------------------
-- [E] 원복 (실험 후 정리)
-- ---------------------------------------------------------------------
-- ALTER TABLE source_payment DROP INDEX idx_payment_cover;
-- ALTER TABLE source_order   DROP INDEX idx_order_cover;
-- ALTER TABLE mismatch       DROP INDEX idx_mismatch_created;
