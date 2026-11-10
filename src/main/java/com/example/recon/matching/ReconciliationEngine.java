package com.example.recon.matching;

import com.example.recon.mismatch.MismatchType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 대조 엔진 v1 (baseline).
 *
 * 동작
 *  1. A, B 를 각각 key -> Row 맵으로 만든다
 *  2. A 를 돌며 B 에 없으면 MISSING_IN_B, 있으면 값을 비교해 다르면 VALUE_DIFF
 *  3. B 에만 있는 키는 MISSING_IN_A
 *
 * 한계 (의도적)
 *  - 같은 키가 2건 이상이면 마지막 행만 남는다 -> DUPLICATE 를 탐지하지 못한다 (#8)
 *  - 비교 규칙이 코드에 박혀 있다 (#7 에서 Strategy 로 분리)
 *  - 전체 데이터를 메모리에 올린다 (#14 에서 청크 처리)
 */
public final class ReconciliationEngine {

    public ReconciliationResult reconcile(List<Row> a, List<Row> b) {
        Map<String, Row> mapA = new HashMap<>();
        for (Row r : a) {
            mapA.put(r.key(), r);
        }
        Map<String, Row> mapB = new HashMap<>();
        for (Row r : b) {
            mapB.put(r.key(), r);
        }

        List<MismatchItem> items = new ArrayList<>();
        for (Map.Entry<String, Row> e : mapA.entrySet()) {
            Row ra = e.getValue();
            Row rb = mapB.get(e.getKey());
            if (rb == null) {
                items.add(new MismatchItem(e.getKey(), MismatchType.MISSING_IN_B, ""));
            } else if (ra.amount() != rb.amount() || !ra.status().equals(rb.status())) {
                items.add(new MismatchItem(e.getKey(), MismatchType.VALUE_DIFF, ""));
            }
        }
        for (String key : mapB.keySet()) {
            if (!mapA.containsKey(key)) {
                items.add(new MismatchItem(key, MismatchType.MISSING_IN_A, ""));
            }
        }
        items.sort(Comparator.comparing(MismatchItem::key));
        return new ReconciliationResult(items);
    }
}
