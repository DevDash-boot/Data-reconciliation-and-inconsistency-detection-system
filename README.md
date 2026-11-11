# Recon — 결제·주문 데이터 대조 및 불일치 탐지 시스템

> 외부 결제 내역(PortOne)과 내부 주문 DB를 대조해 **누락·중복·값 불일치**를 찾아내고, 처리 상태까지 관리하는 백엔드 시스템입니다.
> 100만 건 규모를 목표로 **정확성을 정답 데이터로 검증**하고, **성능 개선 과정을 수치로 기록**했습니다.

## 1. 문제와 해결

결제 시스템(PG사)과 주문 시스템은 따로 움직여서 데이터가 어긋날 수 있습니다. (결제는 됐는데 주문이 없다, 같은 결제가 두 번 기록됐다, 금액이 다르다…)
수작업으로 비교하면 놓치기 쉽고, 건수가 늘면 불가능해집니다.

Recon 은 두 CSV 를 업로드하면 매칭 키(`merchant_uid = order_no`) 기준으로 자동 대조해 불일치를 유형별로 분류하고,
`OPEN → RESOLVED / IGNORED` 로 처리 상태를 관리합니다.

## 2. 주요 기능

| 기능 | 설명 |
|---|---|
| CSV 업로드 | 결제/주문 CSV 파싱, 행 단위 검증(오류 행은 건너뛰고 리포트), BOM 처리 |
| 대조 엔진 | 매칭 키 기반, 범위 규칙(결제 ready/failed, 주문 PENDING 제외), `MatchingStrategy` 로 규칙 교체 |
| 불일치 분류 | `MISSING_IN_A`, `MISSING_IN_B`, `DUPLICATE`, `VALUE_DIFF` (키 하나당 1건, 우선순위 규칙) |
| Job 관리 | 실행 이력, **멱등성**(같은 요청 재사용 / force 재계산 / 동시 실행 방지), 서버 재시작 복구 |
| 대용량 처리 | JDBC batch 적재, 해시 파티션 **청크 처리**(메모리 1/N), 인덱스 분석 |
| 주기 대조 | `@Scheduled` (기본 꺼짐) |
| 조회/관리 | 필터 + 페이징 API, 상태 변경(전이 규칙, 일괄 변경, 이력), 화면 |
| 대시보드 | 유형별/상태별/일별 추이 (Chart.js) |
| 권한 | ADMIN / USER, 화면은 세션 로그인, API 는 HTTP Basic |

### 불일치 유형
| 유형 | 의미 |
|---|---|
| `MISSING_IN_A` | 주문은 있는데 결제 기록이 없다 |
| `MISSING_IN_B` | 결제됐는데 주문이 없다 |
| `DUPLICATE` | 한쪽에 같은 키가 2건 이상 |
| `VALUE_DIFF` | 키는 같지만 금액/상태가 다르다 (필드별 a/b 값을 JSON 으로 저장) |

## 3. 기술 스택

Java 21 · Spring Boot 4.1 · Spring Data JPA + JDBC · Spring Security · MySQL 8.4 · Docker Compose ·
Mustache + Bootstrap + Chart.js · JUnit 5 + AssertJ (+ Mockito)

## 4. 아키텍처

```
CSV ─▶ [upload] 파싱·검증 ─▶ upload_batch + source_payment / source_order   (JDBC batch)
                                             │
                    POST /api/jobs ─▶ [job] ReconciliationJobService
                                             │  범위 규칙 → ReconciliationEngine(MatchingStrategy)
                                             │  IN_MEMORY  : 전체 한 번에
                                             │  CHUNKED    : CRC32 해시로 N 개 청크
                                             ▼
                                       mismatch (+ reconciliation_job 요약)
                                             │
                  [mismatch] 조회·상태 변경(이력) ─▶ [dashboard] 집계 ─▶ 화면 / API
```

자세한 설계: [Wiki](../../wiki) — 도메인 정의, ERD, 알고리즘, 성능 기록, ADR

## 5. 실행 방법

### 준비
- JDK 21, Docker

```bash
git clone https://github.com/{username}/{repo}.git && cd {repo}
cp .env.example .env          # DB 비밀번호와 ADMIN_USERNAME / ADMIN_PASSWORD 를 바꾸세요
docker compose up -d          # MySQL
./gradlew bootRun             # http://localhost:8080  (ADMIN 계정은 .env 의 값으로 자동 생성)
```

### 샘플 데이터로 써보기
```bash
./gradlew generateSample -Psize=10000 -PoutDir=data/10k    # 정답(expected)이 함께 만들어진다

curl -u admin:비밀번호 -F type=PAYMENT -F file=@data/10k/payments.csv localhost:8080/api/uploads
curl -u admin:비밀번호 -F type=ORDER   -F file=@data/10k/orders.csv   localhost:8080/api/uploads
curl -u admin:비밀번호 -X POST localhost:8080/api/jobs -H 'Content-Type: application/json' \
     -d '{"paymentBatchId":1,"orderBatchId":2,"strategy":"EXACT"}'
```
결과는 `data/10k/expected.properties` 와 같아야 합니다. 화면: `/dashboard`, `/mismatches`

### 테스트
```bash
./gradlew test                 # 단위 + 통합 (H2 사용, Docker 불필요)
```
커버리지: `build/reports/jacoco/test/html/index.html`

## 6. API

| Method | URL | 권한 | 설명 |
|---|---|---|---|
| POST | `/api/uploads` | ADMIN | CSV 업로드 (`type=PAYMENT\|ORDER`, `file`) |
| GET | `/api/uploads` | USER | 업로드 이력 |
| POST | `/api/jobs` | ADMIN | 대조 실행 (`strategy`, `mode`, `force`) — 새로 실행 201, 재사용 200 |
| GET | `/api/jobs`, `/api/jobs/{id}` | USER | Job 조회 |
| GET | `/api/mismatches` | USER | 불일치 목록 (`jobId,type,status,keyword,page,size`) |
| GET | `/api/mismatches/{id}`, `/{id}/history` | USER | 상세, 상태 변경 이력 |
| PATCH | `/api/mismatches/{id}/status` | ADMIN | 상태 변경 |
| PATCH | `/api/mismatches/status` | ADMIN | 일괄 상태 변경 (전부 또는 전무) |
| GET | `/api/dashboard/summary` | USER | 통계 |

## 7. 성능

> 측정 환경: (CPU / RAM / OS / JDK / MySQL 버전 — PerfRunner 출력값)
> 측정 방법: `./gradlew bootRun -Pxmx=1g --args="--spring.profiles.active=perf --perf.dataset=data/100k --perf.label=..."`
> 3회 평균, 워밍업 1회 제외, 모든 실행에서 expected 와 100% 일치 확인

| 데이터 규모 | 방식 | 업로드 적재 | 대조 | 결과 저장 | 합계 | 비고 |
|---|---|---|---|---|---|---|
| 10만 | baseline (JPA saveAll) | - | - | - | - | |
| 10만 | JDBC batch | - | - | - | - | |
| 100만 | IN_MEMORY | - | - | - | - | |
| 100만 | CHUNKED (16) | - | - | - | - | 힙 -Xmx 제한 |

상세 기록과 분석: [Wiki - 성능 측정 기록](../../wiki)

## 8. 설계 결정 (요약)

| 결정 | 이유 |
|---|---|
| 상태값을 원본 그대로 저장, 대조 시점에 매핑 | 매핑 규칙이 바뀌어도 재업로드가 필요 없다 |
| 키 하나당 불일치 1건 + 우선순위 | 같은 문제가 여러 건으로 부풀지 않게 |
| `MatchingStrategy` 로 비교 규칙 분리 | 엔진 수정 없이 규칙 추가 |
| 적재/저장은 JDBC batch | IDENTITY PK 에서 JPA `saveAll` 은 행마다 INSERT |
| 청크는 CRC32 해시 파티션 | 같은 키는 항상 같은 청크 → 경계에서 누락/중복이 없다 |
| 멱등성은 DB UNIQUE(`active_key`) 로 최종 방어 | 서버가 여러 대여도, 동시 요청이어도 안전 |
| CSV 파서 직접 구현 | 이 프로젝트에 필요한 범위가 작고 테스트로 검증 가능 |

## 9. 한계와 다음 단계

- 퍼지(유사) 매칭, Excel 입력, 부분 취소·분할 결제 미지원
- 자정 전후 시차 보정 없음 (날짜 비교를 하지 않음)
- Job 은 동기 실행 (요청 스레드에서 끝날 때까지 대기) → 비동기 + 진행률
- 청크 쿼리가 청크마다 batch 범위를 다시 훑음 → 키 해시 컬럼 저장 후 범위 조회로 개선 가능
- 테스트 DB 는 H2 → MySQL 전용 경로(CHUNKED)는 Testcontainers 로 검증 필요

## 10. 프로젝트 관리

Issues(작업 단위) · Projects(진행 보드) · Wiki(설계/ADR/성능 기록) 로 운영했습니다. 브랜치 `feature/{이슈번호}-{설명}`, 커밋 `feat: / fix: / test: / docs: / perf: / chore:`

## 11. 회고

(프로젝트 종료 후 작성: 가장 어려웠던 문제 3가지, 선택과 이유, 아쉬운 점)
