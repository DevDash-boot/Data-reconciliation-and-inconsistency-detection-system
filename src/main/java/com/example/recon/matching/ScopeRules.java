package com.example.recon.matching;

import java.util.ArrayList;
import java.util.List;

/**
 * 도메인 정의(Wiki 2번)의 "대조 대상 범위" 규칙.
 *  A(결제): paid, cancelled 만 대조 / ready, failed 제외
 *  B(주문): PAID, CANCELED 만 대조 / PENDING 등 제외
 * 대조 범위 안의 A 상태는 B 와 비교할 수 있도록 PAID / CANCELED 로 맞춘다.
 */
public final class ScopeRules {

    /** 범위 안의 행(정규화됨)과 제외된 행 수 */
    public record Scoped(List<Row> rows, int excluded) {
    }

    private ScopeRules() {
    }

    public static boolean inScopeA(String status) {
        return "paid".equals(status) || "cancelled".equals(status);
    }

    public static boolean inScopeB(String status) {
        return "PAID".equals(status) || "CANCELED".equals(status);
    }

    public static String normalizeA(String status) {
        return switch (status) {
            case "paid" -> "PAID";
            case "cancelled" -> "CANCELED";
            default -> status;
        };
    }

    public static Scoped scopeA(List<Row> raw) {
        List<Row> rows = new ArrayList<>(raw.size());
        int excluded = 0;
        for (Row r : raw) {
            if (inScopeA(r.status())) {
                rows.add(new Row(r.key().trim(), r.amount(), normalizeA(r.status())));
            } else {
                excluded++;
            }
        }
        return new Scoped(rows, excluded);
    }

    public static Scoped scopeB(List<Row> raw) {
        List<Row> rows = new ArrayList<>(raw.size());
        int excluded = 0;
        for (Row r : raw) {
            if (inScopeB(r.status())) {
                rows.add(new Row(r.key().trim(), r.amount(), r.status()));
            } else {
                excluded++;
            }
        }
        return new Scoped(rows, excluded);
    }
}
