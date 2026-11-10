package com.example.recon.matching;

import java.util.ArrayList;
import java.util.List;

/** 금액과 상태가 모두 정확히 같아야 일치. */
public final class ExactMatchStrategy implements MatchingStrategy {

    public static final String NAME = "EXACT";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<FieldDiff> compare(Row a, Row b) {
        List<FieldDiff> diffs = new ArrayList<>(2);
        if (a.amount() != b.amount()) {
            diffs.add(new FieldDiff("amount", String.valueOf(a.amount()), String.valueOf(b.amount())));
        }
        if (!a.status().equals(b.status())) {
            diffs.add(new FieldDiff("status", a.status(), b.status()));
        }
        return diffs;
    }
}
