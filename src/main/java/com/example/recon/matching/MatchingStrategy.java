package com.example.recon.matching;

import java.util.List;

/** 키가 같은 A, B 한 쌍을 어떤 규칙으로 비교할지 정한다. 다른 필드가 없으면 빈 리스트. */
public interface MatchingStrategy {

    String name();

    List<FieldDiff> compare(Row a, Row b);
}
