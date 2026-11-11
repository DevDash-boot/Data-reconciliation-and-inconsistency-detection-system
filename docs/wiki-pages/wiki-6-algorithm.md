# 6. 대조 알고리즘 (최종본)

## v1: 인메모리 Map 방식 (baseline) — #6
### 동작
1. A, B 를 각각 `key → Row` 맵으로 만든다
2. A 를 돌며 B 에 없으면 MISSING_IN_B, 있으면 값을 비교해 다르면 VALUE_DIFF
3. B 에만 있는 키는 MISSING_IN_A
### 복잡도
- 시간 O(N + M), 공간 O(N + M)
### 한계
- 같은 키가 2건 이상이면 마지막 행만 남는다 → DUPLICATE 를 탐지하지 못한다
- 비교 규칙이 코드에 박혀 있다
- 전체를 메모리에 올린다

## v2: Strategy 분리 — #7
- 값 비교를 `MatchingStrategy.compare(a, b)` 로 분리 (EXACT, AMOUNT_ONLY)
- 엔진은 어떤 규칙인지 모르고, 차이 목록이 비어 있는지만 본다
- 새 규칙은 `MatchingStrategies` 에 한 줄 추가

## v3: 분류 규칙 + 중복 처리 — #8
- 같은 키를 **목록**으로 묶어서 개수를 센다
- 키 하나당 불일치 1건, 우선순위: `DUPLICATE > MISSING_* > VALUE_DIFF`
  - 중복이면 어느 행을 비교할지 모호하므로 값 비교를 하지 않는다
- VALUE_DIFF 는 다른 필드를 모두 담는다 (`{"amount":{"a":"100","b":"90"},"status":{...}}`)
- 결과는 키 순서로 정렬 (TreeSet) → 항상 같은 순서, 테스트 비교가 쉽다

## v4: 청크 단위 처리 — #14
### 청크 분할 기준
`CRC32(key) % N` (기본 N=16). 같은 키는 A, B 어디에 있든 **항상 같은 청크**에 들어간다.
### 경계 처리
청크 경계 문제가 구조적으로 없다. 키가 한 청크 안에서만 비교되므로 청크별 결과의 합집합이 전체 결과와 같다.
(검증: `ChunkEquivalenceTest` — 2만 건에서 청크 결과 == 전체 결과)
### SQL 과 Java 의 일치
`MOD(CRC32(col), N)` 은 Java `CRC32` 와 같은 값을 낸다. (MySQL 문서 예시 `CRC32('MySQL') = 3259397556` 로 확인)
### 비용
청크마다 `batch_id` 범위를 다시 훑는다 (청크 수만큼 반복). 개선 아이디어는 #15 EXPLAIN 기록 참고.

## MatchingStrategy 구조
| Strategy | 비교 규칙 | 사용 시점 |
|---|---|---|
| EXACT | 금액 + 상태 | 기본 |
| AMOUNT_ONLY | 금액만 | 상태 표기가 시스템마다 달라 신뢰하기 어려울 때 |

## 멱등성 설계 — #16
- "동일 Job" = 결제 배치 + 주문 배치 + 전략
- 재요청: 기존 SUCCESS 결과 재사용 / `force=true` 면 삭제 후 재계산 / 실행 중이면 409 / FAILED 면 재실행 허용
- 최종 방어: `reconciliation_job.active_key` UNIQUE (PENDING·RUNNING·SUCCESS 동안만 값이 있고 FAILED 면 NULL)
- 서버 재시작으로 RUNNING 이 남으면 시작 시 FAILED 로 정리 (`JobRecovery`)

## 검토했으나 채택하지 않은 방법
| 방법 | 안 쓴 이유 |
|---|---|
| 정렬 후 merge-join 스트리밍 | 메모리는 가장 적지만 두 커서를 동시에 열어야 해서 구현/테스트가 복잡 |
| 퍼지(유사) 매칭 | 이 프로젝트 범위 밖. Strategy 로 확장 가능한 구조만 마련 |
| 불일치 상세를 MySQL JSON 컬럼으로 | 현재는 조회 조건으로 쓰지 않아 문자열로 충분. 필요해지면 재검토 |
