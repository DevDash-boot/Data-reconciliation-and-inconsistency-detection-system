package com.example.recon.matching;

import com.example.recon.mismatch.MismatchType;

/** 대조 결과 불일치 1건. detail 은 JSON 문자열. */
public record MismatchItem(String key, MismatchType type, String detail) {
}
