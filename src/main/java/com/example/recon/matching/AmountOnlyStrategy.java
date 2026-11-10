package com.example.recon.matching;

import java.util.List;

/** 금액만 비교한다. 상태 차이는 무시. */
public final class AmountOnlyStrategy implements MatchingStrategy {

    public static final String NAME = "AMOUNT_ONLY";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<FieldDiff> compare(Row a, Row b) {
        if (a.amount() != b.amount()) {
            return List.of(new FieldDiff("amount", String.valueOf(a.amount()), String.valueOf(b.amount())));
        }
        return List.of();
    }
}
