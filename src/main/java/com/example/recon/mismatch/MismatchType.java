package com.example.recon.mismatch;

public enum MismatchType {
    MISSING_IN_A,   // 키가 B(주문)에만 있다
    MISSING_IN_B,   // 키가 A(결제)에만 있다
    DUPLICATE,      // 한쪽에 같은 키가 2건 이상
    VALUE_DIFF      // 키는 같지만 비교 필드 값이 다르다
}
