package com.example.recon.matching;

import com.example.recon.mismatch.MismatchType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 대조 엔진 v2.
 * v1 과 달라진 점: 값 비교 규칙을 MatchingStrategy 로 분리했다. 엔진은 어떤 규칙인지 모른다.
 * (중복 키 처리는 아직 없다. #8)
 */
public final class ReconciliationEngine {

    private final MatchingStrategy strategy;

    public ReconciliationEngine() {
        this(new ExactMatchStrategy());
    }

    public ReconciliationEngine(MatchingStrategy strategy) {
        this.strategy = strategy;
    }

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
            Row rb = mapB.get(e.getKey());
            if (rb == null) {
                items.add(new MismatchItem(e.getKey(), MismatchType.MISSING_IN_B, ""));
            } else if (!strategy.compare(e.getValue(), rb).isEmpty()) {
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
