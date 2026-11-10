package com.example.recon.matching;

import java.util.List;

/** 이름으로 전략을 찾는다. 새 전략은 여기에 한 줄만 추가하면 된다. */
public final class MatchingStrategies {

    private MatchingStrategies() {
    }

    public static List<String> names() {
        return List.of(ExactMatchStrategy.NAME, AmountOnlyStrategy.NAME);
    }

    public static MatchingStrategy of(String name) {
        if (name == null) {
            throw new IllegalArgumentException("strategy 가 필요합니다. 사용 가능: " + names());
        }
        return switch (name.trim().toUpperCase()) {
            case ExactMatchStrategy.NAME -> new ExactMatchStrategy();
            case AmountOnlyStrategy.NAME -> new AmountOnlyStrategy();
            default -> throw new IllegalArgumentException("알 수 없는 strategy: " + name + " / 사용 가능: " + names());
        };
    }
}
