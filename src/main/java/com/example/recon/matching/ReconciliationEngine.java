package com.example.recon.matching;

import com.example.recon.mismatch.MismatchType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 대조 엔진 v3.
 * v2 와 달라진 점: 같은 키를 목록으로 묶어서 DUPLICATE 를 탐지하고, 키 하나당 불일치 1건 규칙을 지킨다.
 *
 * 우선순위 (Wiki 2번 "유형 우선순위")
 *  1. DUPLICATE    : 한쪽에라도 같은 키가 2건 이상 (어느 행을 비교할지 모호하므로 값 비교는 하지 않는다)
 *  2. MISSING_IN_A / MISSING_IN_B : 키가 한쪽에만 있다
 *  3. VALUE_DIFF   : 양쪽에 1건씩 있고 값이 다르다
 *
 * 결과는 키 순서로 정렬되어 항상 같은 순서로 나온다.
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
        Map<String, List<Row>> groupA = group(a);
        Map<String, List<Row>> groupB = group(b);

        TreeSet<String> keys = new TreeSet<>(groupA.keySet());
        keys.addAll(groupB.keySet());

        List<MismatchItem> items = new ArrayList<>();
        for (String key : keys) {
            List<Row> la = groupA.getOrDefault(key, List.of());
            List<Row> lb = groupB.getOrDefault(key, List.of());

            if (la.size() > 1 || lb.size() > 1) {
                items.add(new MismatchItem(key, MismatchType.DUPLICATE,
                        DiffDetailJson.duplicate(la.size(), lb.size())));
            } else if (la.isEmpty()) {
                items.add(new MismatchItem(key, MismatchType.MISSING_IN_A, DiffDetailJson.missing("B")));
            } else if (lb.isEmpty()) {
                items.add(new MismatchItem(key, MismatchType.MISSING_IN_B, DiffDetailJson.missing("A")));
            } else {
                List<FieldDiff> diffs = strategy.compare(la.get(0), lb.get(0));
                if (!diffs.isEmpty()) {
                    items.add(new MismatchItem(key, MismatchType.VALUE_DIFF, DiffDetailJson.valueDiff(diffs)));
                }
            }
        }
        return new ReconciliationResult(items);
    }

    private static Map<String, List<Row>> group(List<Row> rows) {
        Map<String, List<Row>> map = new HashMap<>();
        for (Row r : rows) {
            map.computeIfAbsent(r.key(), k -> new ArrayList<>(1)).add(r);
        }
        return map;
    }
}
