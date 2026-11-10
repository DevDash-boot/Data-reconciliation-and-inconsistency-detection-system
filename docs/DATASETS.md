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
