# Recon — 결제·주문 데이터 대조 및 불일치 탐지 시스템

> 외부 결제 내역(PortOne)과 내부 주문 DB를 대조해 **누락·중복·값 불일치**를 찾아내고, 처리 상태까지 관리하는 백엔드 시스템입니다.

<!-- 프로젝트명, 한 줄 소개는 자유롭게 수정하세요 -->

## 1. 프로젝트 소개

### 문제
결제 시스템과 주문 시스템은 따로 움직이기 때문에 다음과 같은 불일치가 생길 수 있습니다.
- 결제는 됐는데 주문이 없다 / 주문은 있는데 결제가 없다
- 같은 결제가 두 번 기록됐다
- 금액·상태가 서로 다르다

이런 불일치를 사람이 눈으로 찾기는 어렵고, 데이터가 커질수록 불가능해집니다.

### 목표
1. 두 데이터셋을 **매칭 키 기준으로 자동 대조**한다.
2. 불일치를 **유형별로 분류**하고 저장한다.
3. 불일치를 **OPEN → RESOLVED / IGNORED** 로 관리한다.
4. 100만 건 규모에서도 **측정 가능한 성능**으로 동작한다.

### 범위
| 하는 것 | 안 하는 것 (v1) |
|---|---|
| CSV 업로드 기반 대조 | 실시간 스트리밍 대조 |
| 정확 일치 기반 매칭 | 퍼지 매칭 |
| 3가지 불일치 유형 | Excel 등 다양한 포맷 |
| 관리자 대시보드 | 별도 프론트엔드 프레임워크 |

## 2. 주요 기능

- **CSV 업로드 및 파싱**: 결제 내역 / 주문 내역 업로드
- **대조 엔진**: 매칭 키 기반 비교, `MatchingStrategy` 로 규칙 교체 가능
- **불일치 분류**: `MISSING_IN_A`, `MISSING_IN_B`, `DUPLICATE`, `VALUE_DIFF`
- **대조 Job 관리**: 실행 이력, 재실행, 멱등성 보장
- **주기 대조**: `@Scheduled` 기반 자동 실행
- **불일치 관리**: 조회/필터, 상태 변경(OPEN / RESOLVED / IGNORED)
- **대시보드**: 유형별·기간별 불일치 현황 (Chart.js)
- **권한**: ADMIN / USER 구분

## 3. 불일치 유형

| 유형 | 의미 | 예시 |
|---|---|---|
| `MISSING_IN_A` | A(결제)에는 없고 B(주문)에만 있음 | 주문은 있는데 결제 기록 없음 |
| `MISSING_IN_B` | B(주문)에는 없고 A(결제)에만 있음 | 결제됐는데 주문 없음 |
| `DUPLICATE` | 같은 키가 한쪽에 2건 이상 | 동일 결제 이중 기록 |
| `VALUE_DIFF` | 키는 같지만 값이 다름 | 금액 불일치 |

## 4. 기술 스택

| 구분 | 기술 |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot, Spring Data JPA, Spring Security |
| Database | MySQL 8 |
| Infra | Docker, Docker Compose |
| View | Mustache, Bootstrap, Chart.js |
| Test | JUnit 5, Mockito, AssertJ |

<!-- 실제 사용 버전에 맞게 수정하세요 -->

## 5. 아키텍처

```
[CSV 업로드] → [Parser] → [원천 데이터 테이블 A/B]
                                   ↓
                          [Reconciliation Job]
                                   ↓
                     [MatchingStrategy 기반 대조 엔진]
                                   ↓
                         [Mismatch 결과 테이블]
                                   ↓
                    [조회 API / 상태 관리 / 대시보드]
```

패키지 구조 (예정)
```
com.example.recon
├─ upload        # 파일 업로드, 파싱
├─ source        # 원천 데이터 엔티티/저장소
├─ job           # 대조 Job 실행/이력
├─ matching      # MatchingStrategy, 대조 엔진
├─ mismatch      # 불일치 결과, 상태 관리
├─ dashboard     # 통계
├─ auth          # 인증/인가
└─ common        # 공통 예외, 유틸
```

상세 설계: [Wiki - 아키텍처](../../wiki)

## 6. 성능 측정 결과

> 측정 환경: (CPU / RAM / MySQL 버전 기재)

| 데이터 규모 | 방식 | 소요 시간 | 비고 |
|---|---|---|---|
| 10만 건 | baseline (JPA saveAll) | - | |
| 10만 건 | JDBC batch | - | |
| 100만 건 | 청크 + 인덱스 적용 | - | |

상세 기록: [Wiki - 성능 측정 기록](../../wiki)

## 7. 실행 방법

### 사전 준비
- JDK 17+
- Docker / Docker Compose

### 실행
```bash
# 1. 저장소 클론
git clone https://github.com/{username}/{repo}.git
cd {repo}

# 2. 환경변수 설정
cp .env.example .env

# 3. DB 실행
docker compose up -d

# 4. 애플리케이션 실행
./gradlew bootRun
```

### 샘플 데이터 생성
```bash
# 예: 10만 건, 누락 2%, 중복 1%, 값 불일치 3%
./gradlew bootRun --args="--generator.size=100000 --generator.missing=0.02 --generator.duplicate=0.01 --generator.diff=0.03"
```

### 테스트
```bash
./gradlew test
```

## 8. API 개요

| Method | URL | 설명 |
|---|---|---|
| POST | `/api/uploads` | CSV 업로드 |
| POST | `/api/jobs` | 대조 Job 실행 |
| GET | `/api/jobs/{id}` | Job 상태/요약 조회 |
| GET | `/api/mismatches` | 불일치 목록 (유형/상태 필터) |
| PATCH | `/api/mismatches/{id}/status` | 상태 변경 |
| GET | `/api/dashboard/summary` | 통계 |

<!-- 구현하면서 실제 경로로 수정하세요 -->

## 9. 프로젝트 관리

- **Issues**: 작업 단위 (완료 조건 포함)
- **Projects**: 칸반 보드로 진행 현황 관리
- **Wiki**: 설계, 의사결정(ADR), 성능 기록, 트러블슈팅
- **브랜치 전략**: `main` / `feature/{이슈번호}-{설명}`
- **커밋 컨벤션**: `feat:` `fix:` `test:` `docs:` `refactor:` `perf:` `chore:`

## 10. 로드맵

| 마일스톤 | 기간 | 내용 |
|---|---|---|
| M1 | 1~2주 | 기반 구축 (환경, 도메인 정의, ERD, 샘플 데이터, 업로드) |
| M2 | 3~4주 | 핵심 대조 로직 (엔진, Strategy, 유형 분류, Job) |
| M3 | 5~6주 | 성능·안정성 (batch, 청크, 인덱스, 멱등성, 스케줄) |
| M4 | 7~8주 | 운영 기능·마무리 (관리 화면, 대시보드, 권한, 문서) |

## 11. 회고

> 프로젝트 종료 후 작성: 가장 어려웠던 문제, 선택한 해결 방법, 배운 점

## 12. License

MIT (또는 원하는 라이선스)
