# 7. 성능 측정 기록

## 측정 방법 (매번 똑같이)

```bash
# 1) 데이터셋 생성 (한 번)
bash scripts/gen-datasets.sh

# 2) 측정 (이슈마다 --perf.label 만 바꿔서)
./gradlew bootRun -Pxmx=1g --args="--spring.profiles.active=perf --perf.dataset=data/100k --perf.label=baseline"
```
- PerfRunner 가 환경 정보와 **Wiki 에 붙여넣을 표 행**을 출력한다
- 워밍업 1회 제외, 3회 평균, 매 실행마다 expected 와 100% 일치하는지 확인 (불일치하면 종료 코드 1)
- 이슈별 측정 시점: #12 baseline → #13 JDBC batch → #14 청크(`--perf.mode=CHUNKED`) → #15 인덱스

## 측정 환경
| 항목 | 값 |
|---|---|
| CPU | (PerfRunner 출력: CPU 코어) |
| RAM | |
| OS | |
| JDK | |
| MySQL | |
| JVM 옵션 | -Xmx1g (`-Pxmx`) |

## 기록 표 (PerfRunner 출력을 그대로 붙여넣기)

### 단계별 측정
| 날짜 | 이슈 | 규모(A/B) | 방식 | 업로드 적재 | 대조(로드+비교) | 결과 저장 | 합계 | 메모 |
|---|---|---|---|---|---|---|---|---|
| | #12 | 10만 | baseline (JPA saveAll) | | | | | |
| | #13 | 10만 | JDBC batch (size 1000) | | | | | |
| | #13 | 10만 | JDBC batch (size 5000) | | | | | batch size 비교 |
| | #12 | 100만 | baseline | | | | | 힙 한계/OOM 여부 |
| | #14 | 100만 | IN_MEMORY | | | | | -Xmx 를 줄여가며 |
| | #14 | 100만 | CHUNKED (16) | | | | | 같은 -Xmx |

### 개선 요약
| 개선 항목 | Before | After | 개선율 |
|---|---|---|---|
| JDBC batch (업로드 적재) | | | |
| JDBC 로 3컬럼만 읽기 + batch 저장 | | | |
| 청크 처리 (메모리) | | | |
| 인덱스 (#15) | | | |

## 분석 메모
### 병목 (baseline)
- 가장 느린 단계는? (업로드 적재 / 로드 / 비교 / 저장)
### 가설과 검증
| 가설 | 검증 방법 | 결과 |
|---|---|---|
| IDENTITY PK 라서 saveAll 이 행마다 INSERT 를 보낸다 | Hibernate SQL 로그 / 적재 시간 | |
| rewriteBatchedStatements 없이는 batch 가 효과 없다 | 옵션 on/off 비교 | |
| 청크를 늘리면 메모리는 줄고 시간은 늘어난다 | N = 4 / 16 / 64 비교 | |

## EXPLAIN 기록 (#15)
`15-INDEX-GUIDE.md` 의 양식 사용
