# 성능 측정용 데이터셋 (#11)

| 이름 | 폴더 | 규모(size) | 용도 |
|---|---|---|---|
| 1만 | data/10k | 10,000 | 빠른 확인, 정확성 검증 |
| 10만 | data/100k | 100,000 | 개선 효과 비교의 기준 |
| 100만 | data/1m | 1,000,000 | 메모리/대용량 한계 확인 (#14) |

생성 (한 번이면 충분, 필요하면 언제든 같은 데이터로 재생성)
```bash
bash scripts/gen-datasets.sh                 # macOS / Linux / Git Bash
powershell -ExecutionPolicy Bypass -File scripts\gen-datasets.ps1   # Windows PowerShell
```

각 폴더 구성
- payments.csv, orders.csv : 입력 파일
- expected.properties : 유형별 기대 건수 (대조 결과 검증용)
- expected_mismatches.csv : 기대 불일치 키/유형 목록

기본 비율: 누락(A) 2%, 누락(B) 2%, 중복 1%, 값 불일치 3%, 대조 범위 밖 2%. seed 42 고정.
대용량 파일은 Git 에 커밋하지 않는다 (`.gitignore` 의 `data/`).


## 생성 옵션
`./gradlew generateSample` 에 `-P옵션=값` 으로 넘긴다. 생략하면 기본값을 쓴다.

| 옵션 | 뜻 | 기본값 |
|---|---|---|
| `-Psize` | 기준 행 수 | 1000 |
| `-PmissingA` | 결제에만 없는(A 누락) 비율 | 0.02 |
| `-PmissingB` | 주문에만 없는(B 누락) 비율 | 0.02 |
| `-Pduplicate` | 중복 비율 | 0.01 |
| `-PvalueDiff` | 값 불일치 비율 | 0.03 |
| `-Pexcluded` | 대조 범위 밖 비율 | 0.02 |
| `-Pseed` | 난수 시드 (같으면 같은 데이터) | 42 |
| `-PoutDir` | 출력 폴더 | data/sample |

**PowerShell 에서는 소수점이 있는 값을 따옴표로 감싼다.** 감싸지 않으면 `0.05` 가 `0` 과 `.05` 로 쪼개져 `Task '.05' not found` 오류가 난다.
```powershell
./gradlew generateSample "-Psize=10000" "-PmissingA=0.05" "-PoutDir=data/x"
```
