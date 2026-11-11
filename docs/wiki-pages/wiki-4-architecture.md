# 4. 아키텍처 (최종본)

## 전체 흐름
```
CSV ─▶ [upload] 파싱·검증 ─▶ upload_batch + source_payment / source_order
                                   │
          POST /api/jobs ─▶ ReconciliationJobService
                                   │  범위 규칙(ScopeRules) → ReconciliationEngine(MatchingStrategy)
                                   │  IN_MEMORY / CHUNKED (CRC32 파티션)
                                   ▼
                             mismatch + job 요약
                                   │
              조회·상태 변경(이력) ─▶ 대시보드 ─▶ 화면 / API
```

## 패키지 구조
```
com.example.recon
├─ upload      CsvParser, CsvTable, *CsvMapper, UploadService(JDBC batch), UploadController
├─ source      SourcePayment, SourceOrder, SourceRowWriter
├─ matching    Row, ScopeRules, MatchingStrategy(+Exact/AmountOnly), ReconciliationEngine,
│              DiffDetailJson, HashPartitioner        ← 순수 Java (프레임워크 의존 없음)
├─ job         ReconciliationJob, JobRequest/Response, ReconciliationJobService, RowLoader,
│              JobCleanup, JobRecovery, ReconciliationScheduler
├─ mismatch    Mismatch, MismatchStatus(전이 규칙), MismatchQueryRepository(Criteria),
│              MismatchStatusService, MismatchBatchWriter, 컨트롤러/화면
├─ dashboard   DashboardService(GROUP BY), 컨트롤러/화면
├─ user, auth  AppUser, UserService, AdminInitializer, 로그인/가입 화면
├─ generator   SampleDataGenerator (정답 포함 샘플 데이터)
├─ perf        PerfRunner (perf 프로파일 전용)
└─ common      예외, 보안 설정, 페이지 응답
```

## 설계 원칙
- **핵심 로직은 순수 Java**: 대조 엔진/범위 규칙/CSV 파서/해시 분할은 Spring, JPA 없이 단위 테스트한다.
- **정답이 있는 테스트**: 생성기가 기대 건수와 기대 불일치 키 목록을 함께 만들고, 엔진 결과와 한 건도 다르지 않은지 검증한다.
- **정확성 먼저, 그 다음 성능**: baseline 을 만들고 측정한 뒤 개선한다. 개선 전후 모두 expected 와 일치해야 한다.

## 주요 컴포넌트
| 컴포넌트 | 책임 |
|---|---|
| CsvTable | 헤더 검증, 행 단위 오류 수집, 값 변환 |
| ScopeRules | 대조 범위 판정, A 상태 정규화 |
| ReconciliationEngine | 그룹핑 → 우선순위 규칙 → 유형 분류 (키 순서 정렬 결과) |
| MatchingStrategy | 키가 같은 한 쌍의 비교 규칙 |
| ReconciliationJobService | Job 생성/실행/실패 처리 (트랜잭션 3단계), 멱등성 |
| RowLoader | 대조에 필요한 3 컬럼만 JDBC 로 읽기, 해시 파티션 조회 |
| MismatchBatchWriter, SourceRowWriter | JDBC batch INSERT |

## 트랜잭션 구조 (Job 실행)
1. Job 생성(RUNNING) → **커밋** (실패해도 기록이 남는다)
2. 대조 + 불일치 저장 → **한 트랜잭션** (실패하면 부분 저장 없이 롤백)
3. 실패 시 **별도 트랜잭션**에서 FAILED 기록

## 환경 구성
- 로컬: Docker Compose (MySQL 8.4), 설정은 `.env` → `application.yml`
- 테스트: H2 (MySQL 모드), `application-test.yml`
- 성능 측정: `perf` 프로파일 + PerfRunner
