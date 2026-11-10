#!/usr/bin/env bash
# 성능 측정용 표준 데이터셋 3종(1만 / 10만 / 100만)을 만든다.
#   bash scripts/gen-datasets.sh
# 결과: data/10k, data/100k, data/1m  (각 폴더에 payments.csv, orders.csv, expected.*)
# 같은 seed(42) 라서 언제 다시 만들어도 똑같은 데이터가 나온다. data/ 는 Git 에 올리지 않는다.
set -euo pipefail
cd "$(dirname "$0")/.."

SEED=42
gen() {
  local name="$1" size="$2"
  echo "== $name ($size 건) =="
  ./gradlew --quiet generateSample -Psize="$size" -Pseed="$SEED" -PoutDir="data/$name"
}

gen 10k   10000
gen 100k  100000
gen 1m    1000000

echo
echo "완료. 기대값 확인: cat data/100k/expected.properties"
